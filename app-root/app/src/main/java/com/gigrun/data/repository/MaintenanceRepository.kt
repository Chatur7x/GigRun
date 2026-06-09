package com.gigrun.data.repository

import com.gigrun.data.database.dao.ServiceReminderDao
import com.gigrun.data.database.entities.ServiceReminder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceRepository @Inject constructor(
    private val dao: ServiceReminderDao
) {
    fun getAllReminders(): Flow<List<ServiceReminder>> = dao.getAllReminders()

    suspend fun insert(reminder: ServiceReminder): Long = dao.insert(reminder)
    suspend fun update(reminder: ServiceReminder) = dao.update(reminder)
    suspend fun delete(reminder: ServiceReminder) = dao.delete(reminder)
    suspend fun getReminderById(id: Long): ServiceReminder? = dao.getReminderById(id)
    suspend fun getActiveReminders(): List<ServiceReminder> = dao.getActiveReminders()
    suspend fun snoozeReminder(id: Long, until: Long) = dao.snoozeReminder(id, until)
    suspend fun unsnoozeExpired(now: Long) = dao.unsnoozeExpired(now)

    /**
     * Initializes default maintenance reminders for a vehicle.
     */
    suspend fun initializeDefaults(vehicleName: String, vehicleType: String) {
        val now = System.currentTimeMillis()
        val defaults = mutableListOf(
            ServiceReminder(
                vehicleName = vehicleName, reminderType = "oil",
                lastDoneKm = 0.0, lastDoneDate = now, intervalKm = 2000.0, intervalDays = 60
            ),
            ServiceReminder(
                vehicleName = vehicleName, reminderType = "air_filter",
                lastDoneKm = 0.0, lastDoneDate = now, intervalKm = 5000.0, intervalDays = 120
            ),
            ServiceReminder(
                vehicleName = vehicleName, reminderType = "general",
                lastDoneKm = 0.0, lastDoneDate = now, intervalKm = 6000.0, intervalDays = 180
            ),
            ServiceReminder(
                vehicleName = vehicleName, reminderType = "tyre",
                lastDoneKm = 0.0, lastDoneDate = now, intervalKm = Double.MAX_VALUE, intervalDays = 30
            )
        )
        if (vehicleType == "motorcycle" || vehicleType == "bike" || vehicleType == "scooter") {
            defaults.add(
                ServiceReminder(
                    vehicleName = vehicleName, reminderType = "chain",
                    lastDoneKm = 0.0, lastDoneDate = now, intervalKm = 500.0, intervalDays = 14
                )
            )
        }
        defaults.forEach { insert(it) }
    }
}
