package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // car, bike, auto, scooter, motorcycle, bicycle
    val name: String,          // e.g., "My Activa"
    val company: String,       // e.g., "Honda"
    val model: String,         // e.g., "Activa 6G"
    val currentOdometer: Double,
    val isSelected: Boolean = false
)
