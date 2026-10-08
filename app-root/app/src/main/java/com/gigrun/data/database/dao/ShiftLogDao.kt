package com.gigrun.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gigrun.data.database.entities.ShiftLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftLogDao {
    @Insert suspend fun insert(log: ShiftLog): Long
    @Update suspend fun update(log: ShiftLog)

    @Query("SELECT * FROM shift_logs ORDER BY startTime DESC")
    fun getAll(): Flow<List<ShiftLog>>

    @Query("SELECT * FROM shift_logs WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    fun getRange(from: Long, to: Long): Flow<List<ShiftLog>>

    @Query("SELECT * FROM shift_logs WHERE startTime >= :since ORDER BY startTime DESC")
    fun getSince(since: Long): Flow<List<ShiftLog>>

    @Query("SELECT * FROM shift_logs WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOpenLog(): ShiftLog?

    /** Total minutes actually ridden across the window, open shifts counted to now. */
    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN endTime IS NULL
                 THEN (:now - startTime) / 60000
                 ELSE (endTime - startTime) / 60000
            END
        ), 0)
        FROM shift_logs
        WHERE startTime >= :from AND startTime < :to
    """)
    fun totalRiddenMinutes(from: Long, to: Long, now: Long): Flow<Long>
}