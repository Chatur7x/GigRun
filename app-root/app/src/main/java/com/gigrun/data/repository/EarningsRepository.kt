package com.gigrun.data.repository

import com.gigrun.data.database.dao.EarningDao
import com.gigrun.data.database.entities.Earning
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EarningsRepository @Inject constructor(
    private val earningDao: EarningDao
) {
    fun getEarningsForTrip(tripId: Long): Flow<List<Earning>> = earningDao.getEarningsForTrip(tripId)

    suspend fun insert(earning: Earning): Long = earningDao.insert(earning)
    suspend fun update(earning: Earning) = earningDao.update(earning)
    suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double = earningDao.getTotalEarningsForDay(startOfDay, endOfDay) ?: 0.0
    suspend fun getTotalEarningsSince(startTime: Long): Double = earningDao.getTotalEarningsSince(startTime) ?: 0.0
}
