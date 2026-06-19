package com.gigrun.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gigrun.data.database.dao.*
import com.gigrun.data.database.entities.*

@Database(
    entities = [
        Shift::class,
        Trip::class,
        Earning::class,
        ServiceReminder::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun tripDao(): TripDao
    abstract fun earningDao(): EarningDao
    abstract fun serviceReminderDao(): ServiceReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns a process-wide singleton database instance.
         * Used by services that cannot use Hilt constructor injection.
         * Hilt-injected components should use the Hilt-provided instance from AppModule.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gigrun_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }

        /**
         * Allows Hilt to set the singleton instance so that
         * both DI-provided and manual access use the same database.
         */
        fun setInstance(db: AppDatabase) {
            INSTANCE = db
        }
    }
}
