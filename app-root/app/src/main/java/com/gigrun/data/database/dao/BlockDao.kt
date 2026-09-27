package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Block
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(block: Block): Long

    @Query("SELECT * FROM blocks ORDER BY `index` DESC LIMIT 1")
    suspend fun getLatestBlock(): Block?

    @Query("SELECT * FROM blocks ORDER BY `index` ASC")
    suspend fun getAllBlocks(): List<Block>

    @Query("SELECT * FROM blocks ORDER BY `index` DESC")
    fun getAllBlocksFlow(): Flow<List<Block>>

    @Query("DELETE FROM blocks")
    suspend fun deleteAll()

    @Query("DELETE FROM temp_transactions WHERE id IN (:ids)")
    suspend fun clearMempoolByIds(ids: List<Long>)

    @Transaction
    suspend fun insertBlockAndClearMempool(block: Block, ids: List<Long>): Boolean {
        // IGNORE on duplicate chain height (concurrent-retry reorg): -1 means the
        // block lost the race — keep the mempool so no transaction is ever lost.
        if (insert(block) == -1L) return false
        if (ids.isNotEmpty()) clearMempoolByIds(ids)
        return true
    }
}
