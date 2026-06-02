package com.gigrun.data.repository

import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.database.dao.PlatformStatRow
import com.gigrun.data.database.entities.Trip
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao
) {
    fun getTripsForShift(shiftId: Long): Flow<List<Trip>> = tripDao.getTripsForShift(shiftId)
    fun getTripsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Trip>> = tripDao.getTripsForDay(startOfDay, endOfDay)
    fun getTripsSince(startTime: Long): Flow<List<Trip>> = tripDao.getTripsSince(startTime)

    suspend fun insert(trip: Trip): Long = tripDao.insert(trip)
    suspend fun update(trip: Trip) = tripDao.update(trip)
    suspend fun delete(trip: Trip) = tripDao.delete(trip)
    suspend fun getTripById(id: Long): Trip? = tripDao.getTripById(id)
    suspend fun getActiveTrip(shiftId: Long): Trip? = tripDao.getActiveTrip(shiftId)
    suspend fun getTripCountForDay(startOfDay: Long, endOfDay: Long): Int = tripDao.getTripCountForDay(startOfDay, endOfDay)
    suspend fun getTotalDistanceForDay(startOfDay: Long, endOfDay: Long): Double = tripDao.getTotalDistanceForDay(startOfDay, endOfDay) ?: 0.0
    suspend fun getTotalWaitTimeForDay(startOfDay: Long, endOfDay: Long): Int = tripDao.getTotalWaitTimeForDay(startOfDay, endOfDay) ?: 0
    suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double = tripDao.getTotalEarningsForDay(startOfDay, endOfDay) ?: 0.0
    suspend fun getPlatformStats(startTime: Long): List<PlatformStatRow> = tripDao.getPlatformStats(startTime)
}
