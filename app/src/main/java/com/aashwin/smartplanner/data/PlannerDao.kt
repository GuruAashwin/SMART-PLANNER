package com.aashwin.smartplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    // ---- tasks ----
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY dueDate IS NULL, dueDate, dueMinutes IS NULL, dueMinutes, priority DESC, id")
    fun observeTasks(): Flow<List<TaskFull>>

    @Transaction
    @Query("SELECT * FROM tasks")
    suspend fun allTasksFull(): List<TaskFull>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskFull(id: Long): TaskFull?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    // ---- subtasks ----
    @Insert
    suspend fun insertSubtasks(list: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasks(taskId: Long)

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun getSubtask(id: Long): SubtaskEntity?

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("UPDATE subtasks SET done = :done, focusedMinutes = focusedMinutes + :addMinutes WHERE id = :id")
    suspend fun finishStep(id: Long, done: Boolean, addMinutes: Int)

    @Query("UPDATE subtasks SET done = 1 WHERE taskId = :taskId")
    suspend fun completeAllSubtasks(taskId: Long)

    // ---- reminders ----
    @Insert
    suspend fun insertReminders(list: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteReminders(taskId: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminder(id: Long): ReminderEntity?

    // ---- categories ----
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategory(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    // ---- habits ----
    @Transaction
    @Query("SELECT * FROM habits ORDER BY createdAt")
    fun observeHabits(): Flow<List<HabitWithLogs>>

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun habitDoneCount(habitId: Long, day: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun deleteHabitLog(habitId: Long, day: Long)

    // ---- focus ----
    @Insert
    suspend fun insertFocus(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeFocus(since: Long): Flow<List<FocusSessionEntity>>

    // ---- backup ----
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM subtasks") suspend fun allSubtasks(): List<SubtaskEntity>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<ReminderEntity>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM habits") suspend fun allHabits(): List<HabitEntity>
    @Query("SELECT * FROM habit_logs") suspend fun allHabitLogs(): List<HabitLogEntity>

    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM habits") suspend fun clearHabits()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(list: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSubtasks(list: List<SubtaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminders(list: List<ReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabits(list: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitLogs(list: List<HabitLogEntity>)
}
