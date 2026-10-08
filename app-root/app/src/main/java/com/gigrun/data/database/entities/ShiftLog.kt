package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shift_logs",
    indices = [Index("startTime")]
)
data class ShiftLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val breakCount: Int = 0,
    val totalBreakMinutes: Long = 0,
    // 0..100, higher = more rested. Computed from hours + time-of-day + speed variability.
    val fatigueScore: Int = 100
)