package com.gigrun.data.repository

import com.gigrun.data.database.dao.ShiftDao
import com.gigrun.data.database.entities.Shift
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao
) {
    fun getAllShifts(): Flow<List<Shift>> = shiftDao.getAllShifts()
    fun getShiftsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Shift>> = shiftDao.getShiftsForDay(startOfDay, endOfDay)
    fun getShiftsSince(startTime: Long): Flow<List<Shift>> = shiftDao.getShiftsSince(startTime)

    suspend fun insert(shift: Shift): Long = shiftDao.insert(shift)
    suspend fun update(shift: Shift) = shiftDao.update(shift)
    suspend fun delete(shift: Shift) = shiftDao.delete(shift)
    suspend fun getShiftById(id: Long): Shift? = shiftDao.getShiftById(id)
    suspend fun getActiveShift(): Shift? = shiftDao.getActiveShift()
}
