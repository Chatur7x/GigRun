package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index("expiryDate"), Index("type")]
)
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // rc | insurance | license | aadhaar | pan | gst | e_shram
    val filePath: String,          // encrypted file location, NOT a raw content:// uri
    val expiryDate: Long?,         // null = never expires
    val notes: String? = null
)