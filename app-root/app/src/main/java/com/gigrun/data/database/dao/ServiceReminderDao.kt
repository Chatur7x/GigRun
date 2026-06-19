package com.gigrun.data.database.dao

import androidx.room.*
import com.gigrun.data.database.entities.ServiceReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceReminderDao {
    @Insert
    suspend fun insert(reminder: ServiceReminder): Long

    @Update
    suspend fun update(reminder: ServiceReminder)

    @Delete
    suspend fun delete(reminder: ServiceReminder)

    @Query("SELECT * FROM service_reminders ORDER BY reminderType ASC")
    fun getAllReminders(): Flow<List<ServiceReminder>>

    @Query("SELECT * FROM service_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ServiceReminder?

    @Query("SELECT * FROM service_reminders WHERE isSnoozed = 0")
    suspend fun getActiveReminders(): List<ServiceReminder>

    @Query("UPDATE service_reminders SET isSnoozed = 1, snoozeUntil = :until WHERE id = :id")
    suspend fun snoozeReminder(id: Long, until: Long)

    @Query("UPDATE service_reminders SET isSnoozed = 0, snoozeUntil = NULL WHERE snoozeUntil IS NOT NULL AND snoozeUntil <= :now")
    suspend fun unsnoozeExpired(now: Long)

    @Query("SELECT COUNT(*) FROM service_reminders WHERE vehicleName = :vehicleName")
    suspend fun getCountForVehicle(vehicleName: String): Int

    @Query("DELETE FROM service_reminders WHERE vehicleName = :vehicleName")
    suspend fun deleteAllForVehicle(vehicleName: String)
}
