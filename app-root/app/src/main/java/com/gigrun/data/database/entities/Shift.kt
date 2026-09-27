package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [Index("startTime"), Index("endTime")]
)
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val fuelCostInr: Double? = null,
    val notes: String? = null
)
