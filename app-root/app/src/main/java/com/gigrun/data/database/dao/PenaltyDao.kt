package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Penalty
import kotlinx.coroutines.flow.Flow

@Dao
interface PenaltyDao {
    @Insert suspend fun insert(penalty: Penalty): Long
    @Update suspend fun update(penalty: Penalty)
    @Delete suspend fun delete(penalty: Penalty)

    @Query("SELECT * FROM penalties ORDER BY timestamp DESC")
    fun getAllPenalties(): Flow<List<Penalty>>

    @Query("SELECT * FROM penalties WHERE platform = :platform ORDER BY timestamp DESC")
    fun getByPlatformFlow(platform: String): Flow<List<Penalty>>

    @Query("SELECT * FROM penalties WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getForRange(start: Long, end: Long): Flow<List<Penalty>>

    @Query("SELECT SUM(amountInr) FROM penalties WHERE timestamp >= :start AND timestamp < :end")
    fun getMonthlyTotal(start: Long, end: Long): Flow<Double?>

    @Query("SELECT platform, SUM(amountInr) as total FROM penalties WHERE timestamp >= :start AND timestamp < :end GROUP BY platform")
    fun getPlatformBreakdown(start: Long, end: Long): Flow<List<PenaltyPlatformRow>>

    @Query("SELECT SUM(amountInr) FROM penalties WHERE timestamp >= :start AND timestamp < :end AND isDisputed = 1")
    fun getDisputedTotal(start: Long, end: Long): Flow<Double?>

    @Query("UPDATE penalties SET isDisputed = :disputed WHERE id = :id")
    suspend fun setDisputed(id: Long, disputed: Boolean)

    @Query("DELETE FROM penalties WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class PenaltyPlatformRow(val platform: String, val total: Double?)
