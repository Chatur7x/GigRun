package com.gigrun.data.repository

import com.gigrun.data.database.dao.PenaltyDao
import com.gigrun.data.database.dao.PenaltyPlatformRow
import com.gigrun.data.database.entities.Penalty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PenaltyRepository @Inject constructor(
    private val penaltyDao: PenaltyDao
) {
    fun getAllPenalties(): Flow<List<Penalty>> = penaltyDao.getAllPenalties()

    fun getByPlatform(platform: String): Flow<List<Penalty>> = penaltyDao.getByPlatformFlow(platform)

    fun getForRange(start: Long, end: Long): Flow<List<Penalty>> = penaltyDao.getForRange(start, end)

    fun getMonthlyTotal(start: Long, end: Long): Flow<Double> =
        penaltyDao.getMonthlyTotal(start, end).map { it ?: 0.0 }

    fun getPlatformBreakdown(start: Long, end: Long): Flow<List<PenaltyPlatformRow>> =
        penaltyDao.getPlatformBreakdown(start, end)

    fun getDisputedTotal(start: Long, end: Long): Flow<Double> =
        penaltyDao.getDisputedTotal(start, end).map { it ?: 0.0 }

    suspend fun addPenalty(penalty: Penalty): Long = penaltyDao.insert(penalty)
    suspend fun updatePenalty(penalty: Penalty) = penaltyDao.update(penalty)
    suspend fun deletePenalty(penalty: Penalty) = penaltyDao.delete(penalty)
    suspend fun deleteById(id: Long) = penaltyDao.deleteById(id)

    suspend fun setDisputed(id: Long, disputed: Boolean) =
        penaltyDao.setDisputed(id, disputed)
}
