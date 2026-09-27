package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(expense: Expense): Long
    @Update suspend fun update(expense: Expense)
    @Delete suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getExpensesForRange(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getTotalForRange(start: Long, end: Long): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :start AND timestamp < :end AND isDeductible = 1")
    suspend fun getDeductibleTotalForRange(start: Long, end: Long): Double?

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE timestamp >= :start AND timestamp < :end GROUP BY category")
    suspend fun getCategoryBreakdown(start: Long, end: Long): List<ExpenseCategoryRow>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category AND timestamp >= :start AND timestamp < :end")
    suspend fun getTotalByCategory(category: String, start: Long, end: Long): Double?

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class ExpenseCategoryRow(val category: String, val total: Double?)
