package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trips WHERE shiftId = :shiftId ORDER BY startTime ASC")
    fun getTripsForShift(shiftId: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    fun getTripsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE startTime >= :startTime ORDER BY startTime DESC")
    fun getTripsSince(startTime: Long): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE endTime IS NULL AND shiftId = :shiftId LIMIT 1")
    suspend fun getActiveTrip(shiftId: Long): Trip?

    @Query("SELECT * FROM trips WHERE shiftId = :shiftId ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestTripForShift(shiftId: Long): Trip?

    data class DayTotals(
        val earnings: Double?,
        val distance: Double?,
        val waitSec: Int?,
        val count: Int
    )

    @Query("SELECT SUM(earningInr) AS earnings, SUM(distanceKm) AS distance, SUM(waitTimeSec) AS waitSec, COUNT(*) AS count FROM trips WHERE startTime >= :s AND startTime < :e")
    suspend fun getDayTotals(s: Long, e: Long): DayTotals

    data class SurgeTotals(
        val surge: Double?,
        val bonus: Double?,
        val tip: Double?
    )

    @Query("SELECT SUM(surgeAmount) AS surge, SUM(bonusAmount) AS bonus, SUM(tipAmount) AS tip FROM trips WHERE startTime >= :s AND startTime < :e")
    suspend fun getSurgeTotals(s: Long, e: Long): SurgeTotals

    @Query("SELECT * FROM trips WHERE endTime IS NULL")
    suspend fun getAllOpenTrips(): List<Trip>

    /**
     * Atomic trip-update + earning-insert: the FSM can create a new trip between
     * a separate read and write — this keeps fare attribution on one trip row.
     */
    @Transaction
    suspend fun attachEarning(trip: Trip, earningDao: EarningDao, earning: com.gigrun.data.database.entities.Earning) {
        update(trip)
        earningDao.insert(earning)
    }

    @Query("SELECT COUNT(*) FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getTripCountForDay(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT SUM(distanceKm) FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getTotalDistanceForDay(startOfDay: Long, endOfDay: Long): Double?

    @Query("SELECT SUM(waitTimeSec) FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getTotalWaitTimeForDay(startOfDay: Long, endOfDay: Long): Int?

    @Query("SELECT SUM(earningInr) FROM trips WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double?

    @Query("SELECT platform, COUNT(*) as tripCount, SUM(earningInr) as totalEarnings, SUM(distanceKm) as totalDistance, AVG(waitTimeSec) as avgWaitTime FROM trips WHERE startTime >= :startTime GROUP BY platform")
    suspend fun getPlatformStats(startTime: Long): List<PlatformStatRow>

    /**
     * Feature 28 — per-platform activity inside a half-open window [start, end).
     * Only completed trips count (endTime IS NOT NULL) so in-progress trips never
     * inflate active hours. SQLite integer / 3600000.0 promotes to REAL division.
     */
    @Query("""
        SELECT platform,
               SUM((endTime - startTime) / 3600000.0) as hours,
               SUM(distanceKm) as km,
               COUNT(*) as tripCount
        FROM trips
        WHERE startTime >= :start AND startTime < :end
          AND endTime IS NOT NULL
        GROUP BY platform
    """)
    suspend fun getActivityByPlatform(start: Long, end: Long): List<PlatformActivityRow>
}

data class PlatformActivityRow(
    val platform: String,
    val hours: Double,
    val km: Double,
    val tripCount: Int
)

data class PlatformStatRow(
    val platform: String,
    val tripCount: Int,
    val totalEarnings: Double?,
    val totalDistance: Double?,
    val avgWaitTime: Double?
)
