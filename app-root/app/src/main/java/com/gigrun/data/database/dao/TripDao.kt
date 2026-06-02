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
}

data class PlatformStatRow(
    val platform: String,
    val tripCount: Int,
    val totalEarnings: Double?,
    val totalDistance: Double?,
    val avgWaitTime: Double?
)
