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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        // R&D fix: create channel first so failure notifications can post.
        createNotificationChannel()
        val database = AppDatabase.getInstance(applicationContext)
        val prefs = UserPreferences(applicationContext)

        try {
            // Unsnooze any expired reminders
            database.serviceReminderDao().unsnoozeExpired(System.currentTimeMillis())

            val reminders = database.serviceReminderDao().getActiveReminders()
            val currentOdometer = prefs.accumulatedDistance.first()
            val now = System.currentTimeMillis()

            for (reminder in reminders) {
                // Zero/negative intervals mean "not configured" — never due, never spam.
                if (reminder.intervalKm <= 0 && reminder.intervalDays <= 0) continue
                val kmSinceService = currentOdometer - reminder.lastDoneKm
                val daysSinceService = (now - reminder.lastDoneDate) / (1000 * 60 * 60 * 24)

                val isDueByKm = reminder.intervalKm > 0 && kmSinceService >= reminder.intervalKm
                val isDueByTime = reminder.intervalDays > 0 && daysSinceService >= reminder.intervalDays

                if (isDueByKm || isDueByTime) {
                    val reason = when {
                        isDueByKm && isDueByTime -> "${String.format("%.0f", kmSinceService)} km and ${daysSinceService} days since last service"
                        isDueByKm -> "${String.format("%.0f", kmSinceService)} km since last service"
                        else -> "$daysSinceService days since last service"
                    }

                    sendMaintenanceNotification(
                        reminder.id,
                        formatReminderType(reminder.reminderType),
                        reason
                    )
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance check failed", e)
            // R&D fix: retry only transient failures — fail fast on corrupt/IO bugs
            // to avoid unbounded retry storms.
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
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

    private fun sendMaintenanceNotification(id: Long, title: String, reason: String) {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = intent?.let {
            androidx.core.app.TaskStackBuilder.create(applicationContext).addNextIntentWithParentStack(it)
                .getPendingIntent((id % Int.MAX_VALUE).toInt(), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("🔧 $title Due")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (pending != null) builder.setContentIntent(pending)
        val notification = builder.build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // R&D fix: stable non-colliding ID (old id.toInt()+2000 could collide/overflow).
        val nid = (0x6D000 + (id % 100000)).toInt()
        nm.notify(nid, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Maintenance Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Vehicle maintenance service reminders"
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
