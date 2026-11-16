package com.aashwin.smartplanner.data

import android.content.Context
import androidx.room.withTransaction
import com.aashwin.smartplanner.alarm.FocusMode
import com.aashwin.smartplanner.blocker.BlockerService
import com.aashwin.smartplanner.alarm.Notifier
import com.aashwin.smartplanner.alarm.Scheduler
import com.aashwin.smartplanner.util.Quotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

const val DEFAULT_STEP_MINUTES = 25

/** What an Undo needs: the task as it was, and anything the action created. */
class Undo(val snapshot: TaskFull, val createdId: Long?)

/** All business rules live here, so the UI, notifications and alarm screen behave the same way. */
class Repository(
    private val ctx: Context,
    private val db: PlannerDatabase,
    val prefs: Prefs,
) {
    private val dao = db.dao()

    /**
     * Every change goes through this lock. Without it, two paths running at once
     * (e.g. app start + boot receiver both doing the daily roll-over, or Complete tapped
     * on the notification and in the app) could create duplicate next occurrences.
     */
    private val lock = Mutex()

    val tasks: Flow<List<TaskFull>> = dao.observeTasks()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val habits: Flow<List<HabitWithLogs>> = dao.observeHabits()

    suspend fun getTask(id: Long): TaskFull? = dao.getTaskFull(id)

    /** The "day" in the user's sense: before the day-start hour (default 4 AM) it is still yesterday. */
    fun logicalToday(): LocalDate = LocalDateTime.now().minusHours(prefs.dayStartHour.toLong()).toLocalDate()

    // ---------------- tasks ----------------

    /** Insert or update a task with its checklist and reminders; reschedules its alerts. */
    suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>, reminderOffsets: List<Int>): Long =
        lock.withLock { saveInternal(task, subtasks, reminderOffsets) }

    private suspend fun saveInternal(input: TaskEntity, subtasks: List<SubtaskEntity>, reminderOffsets: List<Int>): Long {
        // Monthly tasks remember their day of month, so the 31st doesn't drift to the 28th.
        val task = if (input.recType == Recurrence.MONTHLY && input.recAnchor == 0 && input.date != null)
            input.copy(recAnchor = input.date!!.dayOfMonth) else input
        val old = if (task.id != 0L) dao.getTaskFull(task.id) else null
        old?.let { cancelAlerts(it) }
        val id = db.withTransaction {
            val id = if (task.id == 0L) dao.insertTask(task) else { dao.updateTask(task); task.id }
            dao.deleteSubtasks(id)
            dao.insertSubtasks(subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = id, position = i) })
            dao.deleteReminders(id)
            dao.insertReminders(reminderOffsets.distinct().sorted().map { ReminderEntity(taskId = id, offsetMinutes = it) })
            id
        }
        val full = dao.getTaskFull(id) ?: return id
        // All checklist items done => the task is done.
        if (full.task.status == Status.PENDING && full.subtasks.isNotEmpty() && full.subtasks.all { it.done }) {
            completeInternal(id, completeSubtasks = false)
        } else {
            Scheduler.schedule(ctx, full.task, full.reminders)
        }
        return id
    }

    private fun cancelAlerts(full: TaskFull) {
        Scheduler.cancel(ctx, full.task, full.reminders)
        Notifier.cancel(ctx, full.task.id)
    }

    /**
     * Completion: status, time, cancel reminders + alarm, then create the next
     * occurrence for recurring tasks. Returns what Undo needs.
     */
    suspend fun completeTask(id: Long, completeSubtasks: Boolean): Undo? = lock.withLock {
        val before = dao.getTaskFull(id) ?: return@withLock null
        if (before.task.status == Status.DONE) return@withLock null
        val created = completeInternal(id, completeSubtasks)
        Undo(before, created)
    }

    /** Returns the id of the next occurrence it created, if any. */
    private suspend fun completeInternal(id: Long, completeSubtasks: Boolean): Long? {
        val full = dao.getTaskFull(id) ?: return null
        if (full.task.status == Status.DONE) return null
        cancelAlerts(full)
        if (prefs.focus.value?.taskId == id) { applyRun(null); FocusMode.exit(ctx, prefs) }
        if (completeSubtasks) dao.completeAllSubtasks(id)
        dao.updateTask(
            full.task.copy(status = Status.DONE, completedAt = System.currentTimeMillis(), snoozedUntil = null)
        )
        return createNextOccurrence(full)
    }

    /** A repeating task's "Due by" moves with its start date. */
    private fun shiftedDeadline(t: TaskEntity, newStart: LocalDate): Long? =
        t.deadlineDate?.let { d -> d + (newStart.toEpochDay() - (t.dueDate ?: newStart.toEpochDay())) }

    private suspend fun createNextOccurrence(full: TaskFull): Long? {
        val t = full.task
        if (!t.isRecurring) return null
        val next = RecurrenceCalc.nextOccurrence(t, logicalToday()) ?: return null
        val newTask = t.copy(
            id = 0, dueDate = next.toEpochDay(), status = Status.PENDING, completedAt = null,
            createdAt = System.currentTimeMillis(), snoozedUntil = null, postponeCount = 0,
            deadlineDate = shiftedDeadline(t, next),
        )
        val newId = saveInternal(newTask, full.sortedSubtasks.map { it.copy(done = false, focusedMinutes = 0) }, full.reminders.map { it.offsetMinutes })
        // the old occurrence stops being "recurring" so it never spawns twice
        dao.getTask(t.id)?.let { dao.updateTask(it.copy(recType = Recurrence.NONE)) }
        return newId
    }

    suspend fun uncompleteTask(id: Long) = lock.withLock {
        val full = dao.getTaskFull(id) ?: return@withLock
        val t = full.task.copy(status = Status.PENDING, completedAt = null)
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, full.reminders)
    }

    /** Ticks a checklist item. Returns an Undo if that finished the whole task. */
    suspend fun toggleSubtask(subId: Long): Undo? = lock.withLock {
        val s = dao.getSubtask(subId) ?: return@withLock null
        val before = dao.getTaskFull(s.taskId)
        dao.updateSubtask(s.copy(done = !s.done))
        val full = dao.getTaskFull(s.taskId) ?: return@withLock null
        if (full.task.status == Status.PENDING && full.subtasks.isNotEmpty() && full.subtasks.all { it.done }) {
            val created = completeInternal(full.task.id, completeSubtasks = false)
            before?.let { Undo(it, created) }
        } else null
    }

    /**
     * "Later" on a start alert rings the start alert again; snoozing a reminder brings the reminder back.
     * Neither completes the task, and neither counts as postponing.
     */
    suspend fun snooze(id: Long, minutes: Int, startAlert: Boolean) = lock.withLock {
        val full = dao.getTaskFull(id) ?: return@withLock
        if (full.task.status != Status.PENDING) return@withLock
        Notifier.cancel(ctx, id)
        val at = System.currentTimeMillis() + minutes * 60_000L
        if (startAlert) {
            val t = full.task.copy(snoozedUntil = at)
            dao.updateTask(t)
            Scheduler.schedule(ctx, t, emptyList())
        } else {
            Scheduler.scheduleReminderSnooze(ctx, id, at)
        }
    }

    /** "Skip" on a start alert: a repeating task moves to its next date, a one-time task to tomorrow. */
    suspend fun skip(id: Long) = lock.withLock {
        val full = dao.getTaskFull(id) ?: return@withLock
        val t = full.task
        if (t.status != Status.PENDING) return@withLock
        cancelAlerts(full)
        if (t.isRecurring) {
            val from = maxOf(t.date ?: logicalToday(), logicalToday())
            val next = RecurrenceCalc.advance(from, t)
            val moved = t.copy(dueDate = next.toEpochDay(), deadlineDate = shiftedDeadline(t, next), snoozedUntil = null)
            dao.updateTask(moved)
            Scheduler.schedule(ctx, moved, full.reminders)
        } else {
            val base = maxOf(t.date ?: LocalDate.now(), LocalDate.now().minusDays(1))
            rescheduleInternal(id, base.plusDays(1), t.dueMinutes)
        }
    }

    suspend fun clearSnooze(id: Long): Unit = lock.withLock {
        val t = dao.getTask(id) ?: return@withLock
        if (t.snoozedUntil != null) dao.updateTask(t.copy(snoozedUntil = null))
    }

    fun dismissAlarm(id: Long) = Notifier.cancel(ctx, id)

    suspend fun reschedule(id: Long, date: LocalDate?, minutes: Int?) = lock.withLock { rescheduleInternal(id, date, minutes) }

    private suspend fun rescheduleInternal(id: Long, date: LocalDate?, minutes: Int?) {
        val full = dao.getTaskFull(id) ?: return
        cancelAlerts(full)
        val t = full.task.copy(
            dueDate = date?.toEpochDay(), dueMinutes = if (date == null) null else minutes,
            snoozedUntil = null, postponeCount = full.task.postponeCount + 1,
        )
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, full.reminders)
    }

    suspend fun postponeOneDay(id: Long) = lock.withLock {
        val t = dao.getTask(id) ?: return@withLock
        val base = maxOf(t.date ?: LocalDate.now(), LocalDate.now().minusDays(1))
        rescheduleInternal(id, base.plusDays(1), t.dueMinutes)
    }

    /** "Move to today" for overdue tasks. A time that has already passed today is dropped (due by end of day). */
    suspend fun moveToToday(ids: List<Long>) = lock.withLock {
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        for (id in ids) {
            val t = dao.getTask(id) ?: continue
            if (t.status != Status.PENDING) continue
            val m = t.dueMinutes?.takeIf { it > nowMin }
            rescheduleInternal(id, LocalDate.now(), m)
            dao.getTask(id)?.let { moved ->
                // A "Due by" that has already passed would keep it overdue: it becomes "by the end of today".
                if (moved.deadlineDate != null && (moved.deadlineMillis() ?: 0) < System.currentTimeMillis()) {
                    dao.updateTask(moved.copy(deadlineDate = LocalDate.now().toEpochDay(), deadlineMinutes = null))
                }
            }
        }
    }

    suspend fun deleteTask(id: Long): Undo? = lock.withLock {
        val full = dao.getTaskFull(id) ?: return@withLock null
        cancelAlerts(full)
        dao.deleteTask(id)
        Undo(full, null)
    }

    /** Puts a task back exactly as it was before Complete or Delete. */
    suspend fun undo(u: Undo) = lock.withLock {
        u.createdId?.let { cid ->
            dao.getTaskFull(cid)?.let { cancelAlerts(it) }
            dao.deleteTask(cid)
        }
        val f = u.snapshot
        db.withTransaction {
            if (dao.getTask(f.task.id) != null) dao.updateTask(f.task) else dao.putTasks(listOf(f.task))
            dao.deleteSubtasks(f.task.id); dao.putSubtasks(f.subtasks)
            dao.deleteReminders(f.task.id); dao.putReminders(f.reminders)
        }
        Scheduler.schedule(ctx, f.task, f.reminders)
    }

    /** Creates a real task that rings its alarm in about 2 minutes, to check the phone lets alarms through. */
    suspend fun createTestAlarm(): LocalDateTime = lock.withLock {
        val at = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
        saveInternal(
            TaskEntity(
                title = "Test alarm", description = "Created from Settings to check alarms work. Complete it or delete it.",
                dueDate = at.toLocalDate().toEpochDay(), dueMinutes = at.hour * 60 + at.minute, alarmEnabled = true,
            ),
            emptyList(), emptyList(),
        )
        at
    }

    // ---------------- daily handling ----------------

    /**
     * Run at the start of each day (and on app start). Unfinished one-time tasks stay
     * overdue. An unfinished repeating task simply moves to its next date (checklist reset),
     * so it never piles up copies.
     */
    suspend fun rollover() = lock.withLock {
        val today = logicalToday()
        dao.allTasksFull()
            .filter { it.task.status == Status.PENDING && it.task.isRecurring && it.task.dueDate != null && it.task.dueDate < today.toEpochDay() }
            .forEach { full ->
                cancelAlerts(full)
                val next = RecurrenceCalc.nextOccurrence(full.task, today) ?: return@forEach
                val moved = full.task.copy(
                    dueDate = next.toEpochDay(), deadlineDate = shiftedDeadline(full.task, next),
                    snoozedUntil = null, postponeCount = 0,
                )
                dao.updateTask(moved)
                if (full.subtasks.any { it.done }) {
                    dao.deleteSubtasks(moved.id)
                    dao.insertSubtasks(full.sortedSubtasks.map { it.copy(id = 0, done = false, focusedMinutes = 0) })
                }
                Scheduler.schedule(ctx, moved, full.reminders)
            }
        Scheduler.scheduleRollover(ctx, prefs.dayStartHour)
    }

    /** Re-arm every alert, e.g. after a reboot, time change or app update. */
    suspend fun rescheduleAll() = lock.withLock { rescheduleAllInternal() }

    private suspend fun rescheduleAllInternal() {
        dao.allTasksFull().filter { it.task.status == Status.PENDING }
            .forEach { Scheduler.schedule(ctx, it.task, it.reminders) }
        Scheduler.scheduleRollover(ctx, prefs.dayStartHour)
        scheduleSummary()
        dao.allHabits().forEach { Scheduler.scheduleHabit(ctx, it) }
        prefs.focus.value?.let { r -> FocusMachine.nextAlarm(r)?.takeIf { it > System.currentTimeMillis() }?.let { Scheduler.scheduleFocus(ctx, it) } }
    }

    fun scheduleSummary() {
        if (prefs.summaryEnabled) Scheduler.scheduleSummary(ctx, prefs.summaryMinutes) else Scheduler.cancelSummary(ctx)
    }

    /** Morning summary: covers tasks with a date but no time, which have no reminder of their own. */
    suspend fun showSummary() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val all = dao.allTasksFull().map { it.task }.filter { it.status == Status.PENDING }
        val todays = all.filter { it.dueDate == today }.sortedWith(compareBy({ it.dueMinutes ?: Int.MAX_VALUE }, { -it.priority }))
        val overdue = all.count { it.isOverdue(now) && it.dueDate != today }
        if (todays.isNotEmpty() || overdue > 0) Notifier.showSummary(ctx, todays, overdue)
        scheduleSummary()
    }

    // ---------------- categories ----------------

    suspend fun addCategory(name: String): Long = dao.insertCategory(CategoryEntity(name = name.trim()))
    suspend fun deleteCategory(id: Long) { dao.clearCategory(id); dao.deleteCategory(id) }

    // ---------------- habits ----------------

    suspend fun addHabit(name: String, reminderMinutes: Int?) {
        val id = dao.insertHabit(HabitEntity(name = name.trim(), reminderMinutes = reminderMinutes))
        dao.getHabit(id)?.let { Scheduler.scheduleHabit(ctx, it) }
    }

    suspend fun updateHabit(id: Long, name: String, reminderMinutes: Int?) {
        val h = dao.getHabit(id) ?: return
        val updated = h.copy(name = name.trim(), reminderMinutes = reminderMinutes)
        dao.updateHabit(updated)
        Scheduler.cancelHabit(ctx, id)
        Scheduler.scheduleHabit(ctx, updated)
    }

    suspend fun deleteHabit(id: Long) {
        Scheduler.cancelHabit(ctx, id)
        Notifier.cancelHabit(ctx, id)
        dao.deleteHabit(id)
    }

    suspend fun setHabitDone(habitId: Long, day: LocalDate, done: Boolean) {
        if (done) dao.insertHabitLog(HabitLogEntity(habitId, day.toEpochDay()))
        else dao.deleteHabitLog(habitId, day.toEpochDay())
        if (done && day == logicalToday()) Notifier.cancelHabit(ctx, habitId)
    }

    /** Daily habit reminder: only shown if the habit isn't done yet today. */
    suspend fun habitAlert(habitId: Long) {
        val h = dao.getHabit(habitId) ?: return
        if (h.reminderMinutes == null) return
        if (dao.habitDoneCount(habitId, logicalToday().toEpochDay()) == 0) Notifier.showHabit(ctx, h)
        Scheduler.scheduleHabit(ctx, h)
    }

    // ---------------- focus engine ----------------

    val focusSessions: Flow<List<FocusSessionEntity>> =
        dao.observeFocus(System.currentTimeMillis() - 31L * 24 * 3600 * 1000)

    suspend fun allTasks(): List<TaskFull> = dao.allTasksFull()

    /** Saves the session, arms the one alarm it needs, and updates its notification. */
    private fun applyRun(r: FocusRun?) {
        prefs.setFocus(r)
        Scheduler.cancelFocus(ctx)
        if (r == null) { BlockerService.stop(ctx); Notifier.cancelFocusRunning(ctx); return }
        FocusMachine.nextAlarm(r)?.let { Scheduler.scheduleFocus(ctx, it) }
        Notifier.showFocusRunning(ctx, r)
    }

    private fun minutesOf(ms: Long) = ((ms + 30_000L) / 60_000L).toInt()

    /** Saves the focus time of the current step (and ticks it if [finished]). */
    private suspend fun bankStep(r: FocusRun, spentMs: Long, finished: Boolean) {
        val mins = minutesOf(spentMs)
        r.step.subtaskId?.let { dao.finishStep(it, finished, mins) }
        if (mins >= 1) dao.insertFocus(
            FocusSessionEntity(taskId = r.taskId, title = r.step.title, startedAt = r.stepStartedAt, minutes = mins, subtaskId = r.step.subtaskId)
        )
    }

    /**
     * Starts focus on a task: its unfinished steps one by one, each with its own time.
     * A task without steps (or no task) is one block of [minutes].
     */
    suspend fun startFocus(taskId: Long?, minutes: Int?) = lock.withLock {
        prefs.focus.value?.let { old -> if (old.phase != FocusMachine.BREAK) bankStep(old, old.focusedMs(System.currentTimeMillis()), false) }
        val now = System.currentTimeMillis()
        val full = taskId?.let { dao.getTaskFull(it) }?.takeIf { it.task.status == Status.PENDING }
        val steps = if (full != null) {
            full.sortedSubtasks.filter { !it.done }
                .map { FocusStep(it.id, it.title, if (it.minutes > 0) it.minutes else DEFAULT_STEP_MINUTES) }
                .ifEmpty { listOf(FocusStep(null, full.task.title, minutes ?: full.task.plannedMinutes ?: prefs.focusMinutes)) }
        } else listOf(FocusStep(null, "Focus", minutes ?: prefs.focusMinutes))
        if (full != null) {
            Notifier.cancel(ctx, full.task.id) // stops the start alert if it is ringing
            if (full.task.snoozedUntil != null) dao.updateTask(full.task.copy(snoozedUntil = null))
            Scheduler.cancel(ctx, full.task, emptyList())
        }
        applyRun(FocusMachine.start(full?.task?.id, full?.task?.title ?: "Focus", steps, now))
        FocusMode.enter(ctx, prefs)
        BlockerService.start(ctx, prefs)
        Notifier.focusAlert(ctx, "Focus: ${steps.first().title}", "${steps.first().minutes} min · blocked apps are off-limits", Quotes.next(prefs, Quotes.START), false, chime = false)
    }

    /** The exact alarm fired: time is up for this step, or the break is over. */
    suspend fun focusTimerEnded() = lock.withLock {
        val r = prefs.focus.value ?: return@withLock
        val now = System.currentTimeMillis()
        if (r.phaseEnd == 0L) return@withLock
        if (now < r.phaseEnd - 2_000L) { applyRun(r); return@withLock } // fired early: re-arm
        val n = FocusMachine.timerEnded(r, now)
        applyRun(n)
        if (r.phase == FocusMachine.BREAK) {
            Notifier.focusAlert(ctx, "Break over · ${n.step.title}", "${n.step.minutes} min. Back to it.", Quotes.next(prefs, Quotes.START), false)
        } else {
            Notifier.focusAlert(ctx, "Time's up · ${r.step.title}", "Done, or need more time?", Quotes.next(prefs, Quotes.TIME_UP), true)
        }
    }

    suspend fun focusPause() = lock.withLock { prefs.focus.value?.let { applyRun(FocusMachine.pause(it, System.currentTimeMillis())) } }
    suspend fun focusResume() = lock.withLock { prefs.focus.value?.let { applyRun(FocusMachine.resume(it, System.currentTimeMillis())) } }
    suspend fun focusExtend(minutes: Int) = lock.withLock {
        prefs.focus.value?.let { applyRun(FocusMachine.extend(it, minutes, System.currentTimeMillis())) }
        Notifier.cancelFocusAlert(ctx)
    }
    suspend fun focusSkipBreak() = lock.withLock { prefs.focus.value?.let { applyRun(FocusMachine.skipBreak(it, System.currentTimeMillis())) } }

    /** Current step finished: tick it, save its time, then a 5-minute break, or finish the task. */
    suspend fun focusStepDone() = lock.withLock {
        val r = prefs.focus.value ?: return@withLock
        if (r.phase == FocusMachine.BREAK) return@withLock
        val now = System.currentTimeMillis()
        val (n, spent) = FocusMachine.stepDone(r, now)
        bankStep(r, spent, finished = true)
        if (n == null) {
            applyRun(null)
            FocusMode.exit(ctx, prefs)
            r.taskId?.let { completeInternal(it, completeSubtasks = false) }
            Notifier.focusAlert(ctx, "Done · ${r.taskTitle}", "Task complete.", Quotes.next(prefs, Quotes.DONE), false)
        } else {
            applyRun(n)
            Notifier.focusAlert(
                ctx, "Step done · ${r.step.title}", "5-minute break. Next: ${n.next?.title ?: n.step.title}",
                Quotes.next(prefs, Quotes.DONE), false,
            )
        }
    }

    /** Stop early: the time spent still counts; the current step stays unticked. */
    suspend fun stopFocus() = lock.withLock {
        val r = prefs.focus.value ?: return@withLock
        if (r.phase != FocusMachine.BREAK) bankStep(r, r.focusedMs(System.currentTimeMillis()), false)
        applyRun(null)
        Notifier.cancelFocusAlert(ctx)
        FocusMode.exit(ctx, prefs)
    }

    /** After a restart: re-arm the session's alarm, or catch up if it ended while the phone was off. */
    suspend fun restoreFocus() {
        val r = prefs.focus.value ?: return
        if (r.phaseEnd in 1..System.currentTimeMillis()) focusTimerEnded()
        else lock.withLock { applyRun(r) }
        if (prefs.focus.value != null) BlockerService.start(ctx, prefs)
    }

    // ---------------- backup ----------------

    suspend fun exportJson(): String = Backup.toJson(
        dao.allTasks(), dao.allSubtasks(), dao.allReminders(),
        dao.allCategories(), dao.allHabits(), dao.allHabitLogs(),
    )

    suspend fun importJson(json: String) = lock.withLock {
        val data = Backup.fromJson(json)
        dao.allTasksFull().forEach { cancelAlerts(it) }
        dao.allHabits().forEach { Scheduler.cancelHabit(ctx, it.id) }
        db.withTransaction {
            dao.clearTasks(); dao.clearCategories(); dao.clearHabits()
            dao.putCategories(data.categories)
            dao.putTasks(data.tasks)
            dao.putSubtasks(data.subtasks)
            dao.putReminders(data.reminders)
            dao.putHabits(data.habits)
            dao.putHabitLogs(data.habitLogs)
        }
        rescheduleAllInternal()
    }

    /** Nobody answered the start alert before the ring limit: leave a "Missed start" notification. */
    fun startAlertTimedOut(task: TaskEntity) {
        Notifier.showMissedStart(ctx, task, prefs)
    }

    /** "3 steps · 80 min · Due 8:00 PM" for the start alert. */
    fun startSummary(full: TaskFull): String {
        val open = full.sortedSubtasks.filter { !it.done }
        val parts = mutableListOf<String>()
        if (open.isNotEmpty()) {
            parts += "${open.size} step${if (open.size > 1) "s" else ""}"
            parts += "${open.sumOf { if (it.minutes > 0) it.minutes else DEFAULT_STEP_MINUTES }} min"
        } else full.task.plannedMinutes?.let { parts += "$it min" }
        full.task.deadline?.let { parts += "Due " + com.aashwin.smartplanner.util.Fmt.due(it, full.task.deadlineMinutes) }
        return parts.joinToString(" · ").ifEmpty { "Let's go" }
    }
}
