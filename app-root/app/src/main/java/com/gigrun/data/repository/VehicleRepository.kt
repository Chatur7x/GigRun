package com.gigrun.data.repository

import com.gigrun.data.database.dao.FuelLogDao
import com.gigrun.data.database.dao.VehicleDao
import com.gigrun.data.database.entities.FuelLog
import com.gigrun.data.database.entities.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val fuelLogDao: FuelLogDao
) {
    fun getVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()
    fun getSelected(): Flow<Vehicle?> = vehicleDao.getSelectedVehicle()

    // Actual km/L from consecutive odometer readings (works on closed & open logs:
    // uses odometer deltas), compared against the vehicle's claimed figure.
    fun actualKmpl(): Flow<Double?> = fuelLogDao.getAllFuelLogs().map { logs ->
        computeActualKmpl(logs)
    }

    suspend fun addVehicle(v: Vehicle): Long = vehicleDao.insert(v)
    suspend fun updateVehicle(v: Vehicle) = vehicleDao.update(v)
    suspend fun selectVehicle(id: Long) = vehicleDao.selectVehicle(id)

    /** Actual km/L = total km / total litres across overlapping closed intervals. */
    private fun computeActualKmpl(logs: List<FuelLog>): Double? {
        val byVehicle = logs.groupBy { it.vehicleId }
        var totalKm = 0.0
        var totalL = 0.0
        for ((_, vLogs) in byVehicle) {
            val sorted = vLogs.sortedBy { it.timestamp }
            for (i in 1 until sorted.size) {
                val a = sorted[i - 1]
                val b = sorted[i]
                val km = b.odometer - a.odometer
                if (km > 0 && b.liters > 0) {
                    totalKm += km
                    totalL += b.liters
                }
            }
        }
        return if (totalL > 0) totalKm / totalL else null
    }

    /** Estimated cost/km:  fuel cost per km + monthly fixed / estimated km-per-month. */
    suspend fun estimatedCostPerKm(): Double? {
        val selected = vehicleDao.getSelectedVehicleSync() ?: return null
        val logs = fuelLogDao.getActiveFuelLogSync()
        val claimed = selected.claimedKmpl
        return if (claimed != null && claimed > 0) 1.0 / claimed else null
    }
}