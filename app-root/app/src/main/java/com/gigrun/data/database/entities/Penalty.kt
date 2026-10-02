package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "penalties",
    indices = [Index("platform"), Index("timestamp"), Index(value = ["platform", "timestamp"])]
)
data class Penalty(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val amountInr: Double,
    val reason: String,
    val timestamp: Long,
    val isDisputed: Boolean = false
)
