package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_reminders")
data class ServiceReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleName: String,
    val reminderType: String,
    val lastDoneKm: Double = 0.0,
    val lastDoneDate: Long,
    val intervalKm: Double,
    val intervalDays: Int,
    val isSnoozed: Boolean = false,
    val snoozeUntil: Long? = null
)
