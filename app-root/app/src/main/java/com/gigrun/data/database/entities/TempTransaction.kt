package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "temp_transactions", indices = [Index("timestamp")])
data class TempTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // "TRIP" or "FUEL"
    val serializedData: String,     // JSON string
    val timestamp: Long
)
