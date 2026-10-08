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
    val isSelected: Boolean = false,
    // Feature 21 additions — nullable, no DB DEFAULT (Room schema rule from Feature 27).
    val fuelType: String? = null,        // petrol | diesel | ev | cng | bicycle
    val claimedKmpl: Double? = null,     // manufacturer-claimed mileage
    val tankCapacityLitres: Double? = null,
    val emiPerMonth: Double? = null,     // for cost/hour
    val insurancePerYear: Double? = null,
    val phoneBillPerMonth: Double? = null
)
