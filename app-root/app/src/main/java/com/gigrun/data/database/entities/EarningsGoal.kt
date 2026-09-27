package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "earnings_goals")
data class EarningsGoal(
    @PrimaryKey val id: Int = 1,
    val dailyTarget: Double = 800.0,
    val weeklyTarget: Double = 5000.0,
    val monthlyTarget: Double = 20000.0,
    val autoCalculate: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
