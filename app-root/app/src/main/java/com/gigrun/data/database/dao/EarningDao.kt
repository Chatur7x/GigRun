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

    @Query("SELECT * FROM earnings WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getEarningsForTrip(tripId: Long): Flow<List<Earning>>

    /** Idempotency probe: same trip + same fare within the window → re-post, skip. */
    @Query("SELECT COUNT(*) FROM earnings WHERE tripId = :tripId AND amountInr = :amount AND timestamp >= :since")
    suspend fun countSimilar(tripId: Long, amount: Double, since: Long): Int

    @Query("SELECT SUM(amountInr) FROM earnings WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double?

    @Query("SELECT SUM(amountInr) FROM earnings WHERE timestamp >= :startTime")
    suspend fun getTotalEarningsSince(startTime: Long): Double?

    @Query("SELECT platform, SUM(amountInr) as total FROM earnings WHERE timestamp >= :startTime GROUP BY platform")
    suspend fun getEarningsByPlatformSince(startTime: Long): List<PlatformEarningRow>

    /**
     * Feature 28 — per-platform gross revenue inside a half-open window [start, end).
     * Reuses [PlatformEarningRow] rather than declaring a second identical row type.
     */
    @Query("""
        SELECT platform, SUM(amountInr) as total
        FROM earnings
        WHERE timestamp >= :start AND timestamp < :end
        GROUP BY platform
    """)
    suspend fun getRevenueByPlatform(start: Long, end: Long): List<PlatformEarningRow>
}

data class PlatformEarningRow(
    val platform: String,
    val total: Double?
)
