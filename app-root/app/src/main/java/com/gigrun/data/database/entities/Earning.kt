package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "earnings",
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class Earning(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val amountInr: Double,
    val source: String = "notification",
    val timestamp: Long,
    val platform: String
)
