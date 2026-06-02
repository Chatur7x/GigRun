package com.gigrun.data.database

import androidx.room.Database
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
}
