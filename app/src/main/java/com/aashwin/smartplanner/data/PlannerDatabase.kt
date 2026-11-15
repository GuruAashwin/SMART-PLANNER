package com.aashwin.smartplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class, SubtaskEntity::class, ReminderEntity::class,
        CategoryEntity::class, HabitEntity::class, HabitLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun dao(): PlannerDao

    companion object {
        /** v2: monthly anchor day on tasks, daily reminder time on habits. Keeps all existing data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN recAnchor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMinutes INTEGER")
            }
        }

        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
