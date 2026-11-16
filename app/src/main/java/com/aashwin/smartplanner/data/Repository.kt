package com.aashwin.smartplanner.data

import android.content.Context
import androidx.room.withTransaction
import com.aashwin.smartplanner.alarm.Notifier
import com.aashwin.smartplanner.alarm.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
        if (completeSubtasks) dao.completeAllSubtasks(id)
        dao.updateTask(
            full.task.copy(status = Status.DONE, completedAt = System.currentTimeMillis(), snoozedUntil = null)
        )
        return createNextOccurrence(full)
    }

    private suspend fun createNextOccurrence(full: TaskFull): Long? {
        val t = full.task
        if (!t.isRecurring) return null
        val next = RecurrenceCalc.nextOccurrence(t, logicalToday()) ?: return null
        val newTask = t.copy(
            id = 0, dueDate = next.toEpochDay(), status = Status.PENDING, completedAt = null,
            createdAt = System.currentTimeMillis(), snoozedUntil = null, postponeCount = 0,
        )
        val newId = saveInternal(newTask, full.sortedSubtasks.map { it.copy(done = false) }, full.reminders.map { it.offsetMinutes })
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

    /** Snooze never completes the task; it only schedules another alert (and is not counted as postponing). */
    suspend fun snooze(id: Long, minutes: Int) = lock.withLock {
        val full = dao.getTaskFull(id) ?: return@withLock
        if (full.task.status != Status.PENDING) return@withLock
        Notifier.cancel(ctx, id)
        val t = full.task.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, emptyList())
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
                val moved = full.task.copy(dueDate = next.toEpochDay(), snoozedUntil = null, postponeCount = 0)
                dao.updateTask(moved)
                if (full.subtasks.any { it.done }) {
                    dao.deleteSubtasks(moved.id)
                    dao.insertSubtasks(full.sortedSubtasks.map { it.copy(id = 0, done = false) })
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
        prefs.focus.value?.let { f ->
            if (f.endAt > System.currentTimeMillis()) {
                Scheduler.scheduleFocus(ctx, f.endAt)
                Notifier.showFocusRunning(ctx, f.title, f.endAt, f.isBreak)
            }
        }
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

    // ---------------- focus timer ----------------

    val focusSessions: Flow<List<FocusSessionEntity>> =
        dao.observeFocus(System.currentTimeMillis() - 31L * 24 * 3600 * 1000)

    suspend fun allTasks(): List<TaskFull> = dao.allTasksFull()

    /** Starts a focus session (or a break). It keeps running if the app is closed; an alarm marks the end. */
    fun startFocus(taskId: Long?, title: String, minutes: Int, isBreak: Boolean) {
        val now = System.currentTimeMillis()
        val f = FocusState(taskId, title, now, now + minutes * 60_000L, isBreak)
        prefs.setFocus(f)
        if (!isBreak) prefs.focusMinutes = minutes
        Scheduler.scheduleFocus(ctx, f.endAt)
        Notifier.showFocusRunning(ctx, title, f.endAt, isBreak)
    }

    /** Stopped early: the minutes already done still count (if at least 1 and not a break). */
    suspend fun stopFocus() {
        val f = prefs.focus.value ?: return
        Scheduler.cancelFocus(ctx)
        Notifier.cancelFocusRunning(ctx)
        prefs.setFocus(null)
        val done = ((System.currentTimeMillis() - f.startAt) / 60_000L).toInt()
        if (!f.isBreak && done >= 1) dao.insertFocus(FocusSessionEntity(taskId = f.taskId, title = f.title, startedAt = f.startAt, minutes = done))
    }

    /** Called by the end-of-session alarm (and on app start, in case the phone was off at that moment). */
    suspend fun finishFocus() {
        val f = prefs.focus.value ?: return
        if (System.currentTimeMillis() < f.endAt - 2_000L) return
        prefs.setFocus(null)
        Notifier.cancelFocusRunning(ctx)
        if (!f.isBreak) dao.insertFocus(FocusSessionEntity(taskId = f.taskId, title = f.title, startedAt = f.startAt, minutes = f.totalMinutes))
        Notifier.showFocusDone(ctx, f.title, f.totalMinutes, f.isBreak)
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

    /** Called when a task's alarm stops ringing on its own after the time limit. */
    fun alarmTimedOut(task: TaskEntity) {
        Notifier.showMissedAlarm(ctx, task, prefs)
    }
}
