package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Shift
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Insert
    suspend fun insert(shift: Shift): Long

    @Update
    suspend fun update(shift: Shift)

    @Delete
    suspend fun delete(shift: Shift)

    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun getAllShifts(): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShiftById(id: Long): Shift?

    @Query("SELECT * FROM shifts WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveShift(): Shift?

    @Query("SELECT * FROM shifts WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    fun getShiftsForDay(startOfDay: Long, endOfDay: Long): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE startTime >= :startTime ORDER BY startTime DESC")
    fun getShiftsSince(startTime: Long): Flow<List<Shift>>
}
