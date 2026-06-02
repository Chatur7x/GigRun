package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Earning
import kotlinx.coroutines.flow.Flow

@Dao
interface EarningDao {
    @Insert
    suspend fun insert(earning: Earning): Long

    @Update
    suspend fun update(earning: Earning)

    @Query("SELECT * FROM earnings WHERE tripId = :tripId")
    fun getEarningsForTrip(tripId: Long): Flow<List<Earning>>

    @Query("SELECT SUM(amountInr) FROM earnings WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double?

    @Query("SELECT SUM(amountInr) FROM earnings WHERE timestamp >= :startTime")
    suspend fun getTotalEarningsSince(startTime: Long): Double?

    @Query("SELECT platform, SUM(amountInr) as total FROM earnings WHERE timestamp >= :startTime GROUP BY platform")
    suspend fun getEarningsByPlatformSince(startTime: Long): List<PlatformEarningRow>
}

data class PlatformEarningRow(
    val platform: String,
    val total: Double?
)
