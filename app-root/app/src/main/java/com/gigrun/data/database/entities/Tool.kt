package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tools",
    indices = [Index("category"), Index("name")]
)
data class Tool(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,        // Puncture | Hand Tools | Electrical | Safety | Maintenance | Electronics | Fluids
    val description: String,
    val priceInr: Int,
    val buyUrl: String? = null
)