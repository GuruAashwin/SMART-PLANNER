package com.aashwin.smartplanner.data

import android.content.Context
import androidx.room.withTransaction
import com.aashwin.smartplanner.alarm.Notifier
import com.aashwin.smartplanner.alarm.Scheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** All business rules live here, so the UI, notifications and alarm screen behave the same way. */
class Repository(
    private val ctx: Context,
    private val db: PlannerDatabase,
    val prefs: Prefs,
) {
    private val dao = db.dao()

    val tasks: Flow<List<TaskFull>> = dao.observeTasks()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val habits: Flow<List<HabitWithLogs>> = dao.observeHabits()

    suspend fun getTask(id: Long): TaskFull? = dao.getTaskFull(id)

    suspend fun seedDefaults() {
        if (dao.categoryCount() == 0) {
            listOf("College", "Coding", "Fitness", "Personal", "Projects", "Important")
                .forEach { dao.insertCategory(CategoryEntity(name = it)) }
        }
    }

    // ---------------- tasks ----------------

    /** Insert or update a task with its checklist and reminders; reschedules its alerts. */
    suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>, reminderOffsets: List<Int>): Long {
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
            completeTask(id, completeSubtasks = false)
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
     * occurrence for recurring tasks.
     */
    suspend fun completeTask(id: Long, completeSubtasks: Boolean) {
        val full = dao.getTaskFull(id) ?: return
        if (full.task.status == Status.DONE) return
        cancelAlerts(full)
        if (completeSubtasks) dao.completeAllSubtasks(id)
        dao.updateTask(
            full.task.copy(status = Status.DONE, completedAt = System.currentTimeMillis(), snoozedUntil = null)
        )
        createNextOccurrence(full)
    }

    private suspend fun createNextOccurrence(full: TaskFull) {
        val t = full.task
        if (!t.isRecurring) return
        val next = RecurrenceCalc.nextOccurrence(t, LocalDate.now()) ?: return
        val newTask = t.copy(
            id = 0, dueDate = next.toEpochDay(), status = Status.PENDING, completedAt = null,
            createdAt = System.currentTimeMillis(), snoozedUntil = null, postponeCount = 0,
        )
        saveTask(newTask, full.sortedSubtasks.map { it.copy(done = false) }, full.reminders.map { it.offsetMinutes })
        // the old occurrence stops being "recurring" so it never spawns twice
        dao.getTask(t.id)?.let { dao.updateTask(it.copy(recType = Recurrence.NONE)) }
    }

    suspend fun uncompleteTask(id: Long) {
        val full = dao.getTaskFull(id) ?: return
        val t = full.task.copy(status = Status.PENDING, completedAt = null)
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, full.reminders)
    }

    suspend fun toggleSubtask(subId: Long) {
        val s = dao.getSubtask(subId) ?: return
        dao.updateSubtask(s.copy(done = !s.done))
        val full = dao.getTaskFull(s.taskId) ?: return
        if (full.task.status == Status.PENDING && full.subtasks.isNotEmpty() && full.subtasks.all { it.done }) {
            completeTask(full.task.id, completeSubtasks = false)
        }
    }

    /** Snooze never completes the task; it only schedules another alert. */
    suspend fun snooze(id: Long, minutes: Int) {
        val full = dao.getTaskFull(id) ?: return
        Notifier.cancel(ctx, id)
        val t = full.task.copy(
            snoozedUntil = System.currentTimeMillis() + minutes * 60_000L,
            postponeCount = full.task.postponeCount + 1,
        )
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, emptyList())
    }

    suspend fun clearSnooze(id: Long) {
        dao.getTask(id)?.let { if (it.snoozedUntil != null) dao.updateTask(it.copy(snoozedUntil = null)) }
    }

    suspend fun dismissAlarm(id: Long) = Notifier.cancel(ctx, id)

    suspend fun reschedule(id: Long, date: LocalDate?, minutes: Int?) {
        val full = dao.getTaskFull(id) ?: return
        cancelAlerts(full)
        val t = full.task.copy(
            dueDate = date?.toEpochDay(), dueMinutes = if (date == null) null else minutes,
            snoozedUntil = null, postponeCount = full.task.postponeCount + 1,
        )
        dao.updateTask(t)
        Scheduler.schedule(ctx, t, full.reminders)
    }

    suspend fun postponeOneDay(id: Long) {
        val t = dao.getTask(id) ?: return
        val base = maxOf(t.date ?: LocalDate.now(), LocalDate.now().minusDays(1))
        reschedule(id, base.plusDays(1), t.dueMinutes)
    }

    suspend fun duplicate(id: Long): Long? {
        val full = dao.getTaskFull(id) ?: return null
        return saveTask(
            full.task.copy(id = 0, title = full.task.title + " (copy)", status = Status.PENDING, completedAt = null,
                createdAt = System.currentTimeMillis(), snoozedUntil = null, postponeCount = 0),
            full.sortedSubtasks.map { it.copy(done = false) },
            full.reminders.map { it.offsetMinutes },
        )
    }

    suspend fun deleteTask(id: Long) {
        dao.getTaskFull(id)?.let { cancelAlerts(it) }
        dao.deleteTask(id)
    }

    // ---------------- daily handling ----------------

    /**
     * Run at the start of each day (and on app start). Unfinished one-time tasks stay
     * overdue. Unfinished recurring occurrences from earlier days are kept as "missed"
     * in history, and the next occurrence is created.
     */
    suspend fun rollover() {
        val today = LocalDate.now().toEpochDay()
        dao.allTasksFull()
            .filter { it.task.status == Status.PENDING && it.task.isRecurring && (it.task.dueDate ?: today) < today }
            .forEach { full ->
                cancelAlerts(full)
                dao.updateTask(full.task.copy(status = Status.MISSED))
                createNextOccurrence(full)
            }
        Scheduler.scheduleRollover(ctx, prefs.dayStartHour)
    }

    /** Re-arm every alert, e.g. after a reboot, time change or app update. */
    suspend fun rescheduleAll() {
        dao.allTasksFull().filter { it.task.status == Status.PENDING }
            .forEach { Scheduler.schedule(ctx, it.task, it.reminders) }
        Scheduler.scheduleRollover(ctx, prefs.dayStartHour)
    }

    // ---------------- categories ----------------

    suspend fun addCategory(name: String): Long = dao.insertCategory(CategoryEntity(name = name.trim()))
    suspend fun deleteCategory(id: Long) { dao.clearCategory(id); dao.deleteCategory(id) }

    // ---------------- habits ----------------

    suspend fun addHabit(name: String) { dao.insertHabit(HabitEntity(name = name.trim())) }
    suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)
    suspend fun setHabitDone(habitId: Long, day: LocalDate, done: Boolean) {
        if (done) dao.insertHabitLog(HabitLogEntity(habitId, day.toEpochDay()))
        else dao.deleteHabitLog(habitId, day.toEpochDay())
    }

    // ---------------- backup ----------------

    suspend fun exportJson(): String = Backup.toJson(
        dao.allTasks(), dao.allSubtasks(), dao.allReminders(),
        dao.allCategories(), dao.allHabits(), dao.allHabitLogs(),
    )

    suspend fun importJson(json: String) {
        val data = Backup.fromJson(json)
        dao.allTasksFull().forEach { cancelAlerts(it) }
        db.withTransaction {
            dao.clearTasks(); dao.clearCategories(); dao.clearHabits()
            dao.putCategories(data.categories)
            dao.putTasks(data.tasks)
            dao.putSubtasks(data.subtasks)
            dao.putReminders(data.reminders)
            dao.putHabits(data.habits)
            dao.putHabitLogs(data.habitLogs)
        }
        rescheduleAll()
    }
}
