package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "insurance_policies",
    indices = [Index("endDate"), Index("type")]
)
data class InsurancePolicy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val policyNumber: String,
    val type: String,              // third_party | comprehensive | helmet | health | accident
    val premiumInr: Double,
    val startDate: Long,
    val endDate: Long,
    val isPlatformProvided: Boolean = false,
    val claimDeadlineDays: Int = 30
)