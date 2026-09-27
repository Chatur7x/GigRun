package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Insert
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedVehicle(): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedVehicleSync(): Vehicle?

    @Query("UPDATE vehicles SET isSelected = 0")
    suspend fun deselectAll()

    @Transaction
    suspend fun selectVehicle(id: Long) {
        deselectAll()
        setSelected(id)
    }

    @Query("UPDATE vehicles SET isSelected = 1 WHERE id = :id")
    suspend fun setSelected(id: Long)
}
