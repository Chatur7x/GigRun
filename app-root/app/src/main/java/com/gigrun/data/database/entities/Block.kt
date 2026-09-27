package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocks",
    indices = [Index(value = ["index"], unique = true)]
)
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val index: Int,
    val previousHash: String,
    val timestamp: Long,
    val dataPayload: String,
    val nonce: Long,
    val hash: String
)
