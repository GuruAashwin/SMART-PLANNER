package com.aashwin.smartplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class, SubtaskEntity::class, ReminderEntity::class,
        CategoryEntity::class, HabitEntity::class, HabitLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun dao(): PlannerDao

    companion object {
        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").build()
    }
}
