package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(
        entity = Shift::class,
        parentColumns = ["id"],
        childColumns = ["shiftId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("shiftId")]
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val platform: String = "untagged",
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val distanceKm: Double = 0.0,
    val waitTimeSec: Int = 0,
    val pathEncoded: String? = null,
    val earningInr: Double? = null,
    val earningRawNotif: String? = null
)
