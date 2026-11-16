package com.aashwin.smartplanner.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Status {
    const val PENDING = 0
    const val DONE = 1
    const val MISSED = 2 // only in data from version 1.0; repeating tasks now move forward instead
}

object Priority {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2
    fun label(p: Int) = when (p) { HIGH -> "High"; LOW -> "Low"; else -> "Medium" }
}

object Recurrence {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKDAYS = 2   // selected days of the week (bitmask, Monday = bit 0)
    const val WEEKLY = 3
    const val MONTHLY = 4
    const val EVERY_N_DAYS = 5
    fun label(type: Int, interval: Int, days: Int): String = when (type) {
        DAILY -> "Every day"
        WEEKDAYS -> "Every " + (0..6).filter { days and (1 shl it) != 0 }
            .joinToString(", ") { DAY_SHORT[it] }
        WEEKLY -> "Every week"
        MONTHLY -> "Every month"
        EVERY_N_DAYS -> "Every $interval days"
        else -> "Does not repeat"
    }
    val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}

@Entity(tableName = "tasks", indices = [Index("dueDate"), Index("categoryId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val categoryId: Long? = null,
    val priority: Int = Priority.MEDIUM,
    /** Epoch day of the due date (LocalDate.toEpochDay), or null for "no date". */
    val dueDate: Long? = null,
    /** Minutes after midnight, or null for "any time that day". */
    val dueMinutes: Int? = null,
    val status: Int = Status.PENDING,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val alarmEnabled: Boolean = false,
    val recType: Int = Recurrence.NONE,
    val recInterval: Int = 1,
    val recDays: Int = 0,
    /** Day of month a monthly task belongs to (31 stays 31 after a short month). 0 = use the date. */
    @ColumnInfo(defaultValue = "0") val recAnchor: Int = 0,
    val snoozedUntil: Long? = null,
    val postponeCount: Int = 0,
    /** Optional "Due by" (the start is [dueDate]/[dueMinutes]). */
    val deadlineDate: Long? = null,
    val deadlineMinutes: Int? = null,
    /** Planned focus length for a task without steps. */
    val plannedMinutes: Int? = null,
    /** Alert at the start time ("Time to start"). */
    @ColumnInfo(defaultValue = "1") val startAlert: Boolean = true,
) {
    /** [dueDate]/[dueMinutes] hold the START: when to begin working. */
    val date: LocalDate? get() = dueDate?.let { LocalDate.ofEpochDay(it) }
    val time: LocalTime? get() = dueMinutes?.let { LocalTime.of(it / 60, it % 60) }
    val deadline: LocalDate? get() = deadlineDate?.let { LocalDate.ofEpochDay(it) }
    val isRecurring: Boolean get() = recType != Recurrence.NONE

    private fun at(d: LocalDate, minutes: Int?): Long {
        val t = minutes?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.of(23, 59, 59)
        return LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Start moment (date without time = end of that day, for sorting). */
    fun dueMillis(): Long? = date?.let { at(it, dueMinutes) }

    /** When it must be finished: the "Due by" if set, otherwise the end of the start day. */
    fun deadlineMillis(): Long? = deadline?.let { at(it, deadlineMinutes) } ?: date?.let { at(it, null) }

    /** Past the deadline and not done. */
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean {
        if (status != Status.PENDING) return false
        val d = deadlineMillis() ?: return false
        return d < now
    }

    /** Start time has passed but there is still time before the deadline. */
    fun isNotStarted(now: Long = System.currentTimeMillis()): Boolean {
        if (status != Status.PENDING || dueMinutes == null || isOverdue(now)) return false
        val s = dueMillis() ?: return false
        return s < now
    }
}

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
    /** Planned focus minutes for this step. */
    @ColumnInfo(defaultValue = "0") val minutes: Int = 0,
    /** Minutes actually spent in focus on this step. */
    @ColumnInfo(defaultValue = "0") val focusedMinutes: Int = 0,
)

/** A reminder fires [offsetMinutes] before the task's due time. */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val offsetMinutes: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Daily reminder time in minutes after midnight, or null for none. */
    val reminderMinutes: Int? = null,
)

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "day"],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")]
)
data class HabitLogEntity(
    val habitId: Long,
    /** Epoch day the habit was done. */
    val day: Long,
)

/** A finished focus (Pomodoro) session, for the focus history. */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val title: String,
    val startedAt: Long,
    val minutes: Int,
    val subtaskId: Long? = null,
)

data class TaskFull(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val reminders: List<ReminderEntity>,
) {
    val sortedSubtasks: List<SubtaskEntity> get() = subtasks.sortedBy { it.position }
    val subtaskProgress: Float
        get() = if (subtasks.isEmpty()) 0f else subtasks.count { it.done }.toFloat() / subtasks.size
}

data class HabitWithLogs(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val logs: List<HabitLogEntity>,
)
