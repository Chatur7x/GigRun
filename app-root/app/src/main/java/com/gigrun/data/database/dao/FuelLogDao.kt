package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.FuelLog
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLogDao {
    @Insert
    suspend fun insert(fuelLog: FuelLog): Long

    @Update
    suspend fun update(fuelLog: FuelLog)

    @Delete
    suspend fun delete(fuelLog: FuelLog)

    @Query("SELECT * FROM fuel_logs ORDER BY timestamp DESC")
    fun getAllFuelLogs(): Flow<List<FuelLog>>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getFuelLogsForVehicle(vehicleId: Long): Flow<List<FuelLog>>

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND isClosed = 0 LIMIT 1")
    suspend fun getActiveFuelLogForVehicle(vehicleId: Long): FuelLog?

    @Query("SELECT * FROM fuel_logs WHERE isClosed = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveFuelLogSync(): FuelLog?

    @Query("SELECT * FROM fuel_logs WHERE vehicleId = :vehicleId AND timestamp >= :timeLimit ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestFuelLogWithinTime(vehicleId: Long, timeLimit: Long): FuelLog?
}
