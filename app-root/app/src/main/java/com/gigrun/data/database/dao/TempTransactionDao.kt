package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.TempTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TempTransactionDao {
    @Insert
    suspend fun insert(tempTransaction: TempTransaction): Long

    @Query("SELECT * FROM temp_transactions ORDER BY timestamp ASC")
    suspend fun getAllTempTransactions(): List<TempTransaction>

    @Query("SELECT * FROM temp_transactions ORDER BY timestamp DESC")
    fun getAllTempTransactionsFlow(): Flow<List<TempTransaction>>

    @Delete
    suspend fun delete(tempTransaction: TempTransaction)

    @Query("DELETE FROM temp_transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM temp_transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
