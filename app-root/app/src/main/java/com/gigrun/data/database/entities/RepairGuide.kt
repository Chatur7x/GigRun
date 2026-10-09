package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "repair_guides",
    indices = [Index("symptom"), Index("difficulty")]
)
data class RepairGuide(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symptom: String,
    val title: String,
    val stepsJson: String,       // JSON array of step strings
    val difficulty: String,      // Easy | Medium | Hard
    val estimatedCostMinInr: Int,
    val estimatedCostMaxInr: Int,
    val toolsNeededJson: String  // JSON array of tool names
)