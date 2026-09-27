package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "fuel_logs",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class,
        parentColumns = ["id"],
        childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId"), Index("timestamp"), Index("isClosed")]
)
@Serializable
data class FuelLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val amountInr: Double,
    val liters: Double,
    val odometer: Double,
    val timestamp: Long,
    val isClosed: Boolean = false,
    val closedOdometer: Double? = null,
    val calculatedMileage: Double? = null
)
