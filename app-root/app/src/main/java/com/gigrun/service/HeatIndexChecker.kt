package com.gigrun.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gigrun.R
import com.gigrun.core.utils.HeatIndex
import com.gigrun.core.utils.HeatIndexProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Feature 34 — heat index safety check. Called from the existing tracking
 * service's 30-second tick (no new service). Network work is cached to at most
 * once per hour via [HeatIndexProvider].
 */
object HeatIndexChecker {

    private const val NOTIFICATION_ID = 1070

    @Suppress("MissingPermission")
    suspend fun checkAndNotify(context: Context, lat: Double, lon: Double) {
        val cond = HeatIndexProvider.current(lat, lon) ?: return
        val hi = HeatIndex.compute(cond.temperatureC, cond.humidity)
        if (hi > 40.0) {
            withContext(Dispatchers.Main) {
                val notif = NotificationCompat.Builder(context, LocationTrackingService.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Heat risk — take a break")
                    .setContentText(
                        "Feels like ${hi.toInt()}°C right now. Stop, hydrate, and rest before continuing."
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(false)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
                } catch (_: SecurityException) {
                    // POST_NOTIFICATIONS not granted — cannot notify.
                }
            }
        }
    }
}