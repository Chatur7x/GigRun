package com.gigrun.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = Shift::class,
        parentColumns = ["id"],
        childColumns = ["shiftId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("shiftId"), Index("timestamp"), Index(value = ["category", "timestamp"])]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long? = null,
    val category: String, // ExpenseCategory.name
    val amount: Double,
    val note: String? = null,
    val receiptUri: String? = null,
    val timestamp: Long,
    val isDeductible: Boolean = true
)

enum class ExpenseCategory(val label: String, val isDeductible: Boolean) {
    FUEL("Fuel", true),
    PARKING("Parking", true),
    REPAIRS("Repairs", true),
    PHONE("Phone/Internet", true),
    FOOD("Food & Water", false),
    TOLL("Tolls", true),
    INSURANCE("Insurance", true),
    OTHER("Other", false)
}
