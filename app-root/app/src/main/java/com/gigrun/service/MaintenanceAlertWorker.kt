package com.gigrun.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs once daily to check vehicle maintenance thresholds.
 * Sends push notifications when service is due based on distance or time.
 */
class MaintenanceAlertWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MaintenanceAlertWorker"
        const val CHANNEL_ID = "gigrun_maintenance"
        const val WORK_NAME = "maintenance_check"

        /** Schedule the daily maintenance check */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MaintenanceAlertWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(context)
        val prefs = UserPreferences(context)

        try {
            // Unsnooze any expired reminders
            database.serviceReminderDao().unsnoozeExpired(System.currentTimeMillis())

            val reminders = database.serviceReminderDao().getActiveReminders()
            val currentOdometer = prefs.accumulatedDistance.first()
            val now = System.currentTimeMillis()

            createNotificationChannel()

            for (reminder in reminders) {
                val kmSinceService = currentOdometer - reminder.lastDoneKm
                val daysSinceService = (now - reminder.lastDoneDate) / (1000 * 60 * 60 * 24)

                val isDueByKm = kmSinceService >= reminder.intervalKm
                val isDueByTime = daysSinceService >= reminder.intervalDays

                if (isDueByKm || isDueByTime) {
                    val reason = when {
                        isDueByKm && isDueByTime -> "${String.format("%.0f", kmSinceService)} km and ${daysSinceService} days since last service"
                        isDueByKm -> "${String.format("%.0f", kmSinceService)} km since last service"
                        else -> "$daysSinceService days since last service"
                    }

                    sendMaintenanceNotification(
                        reminder.id.toInt(),
                        formatReminderType(reminder.reminderType),
                        reason
                    )
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance check failed", e)
            return Result.retry()
        }
    }

    private fun formatReminderType(type: String): String = when (type) {
        "oil" -> "Engine Oil Change"
        "air_filter" -> "Air Filter Check"
        "chain" -> "Chain Lubrication"
        "general" -> "General Service"
        "tyre" -> "Tyre Pressure Check"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    private fun sendMaintenanceNotification(id: Int, title: String, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🔧 $title Due")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id + 2000, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Maintenance Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Vehicle maintenance service reminders"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
