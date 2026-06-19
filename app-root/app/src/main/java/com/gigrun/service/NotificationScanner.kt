package com.gigrun.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gigrun.core.utils.NotificationParser
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.entities.Earning
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Listens for notifications from delivery platform apps.
 * Automatically tags trips to platforms and extracts earnings.
 *
 * Requires user to enable notification access in Settings.
 */
class NotificationScanner : NotificationListenerService() {

    companion object {
        const val TAG = "NotificationScanner"

        /** Package names of delivery apps we monitor */
        val MONITORED_PACKAGES = setOf(
            "com.grofers.delivery",
            "com.blinkit.delivery",
            "com.zepto.delivery",
            "com.shadowfax.delivery",
            "com.zeptonow.delivery",
            "com.rapido.passenger",
            "com.rapido.driver",
            "com.rapido.captain",
            "com.ubercab.driver"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: AppDatabase? = null

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName

        if (packageName !in MONITORED_PACKAGES) return

        val extras = notification.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        Log.d(TAG, "Notification from $packageName: title=$title, text=$text")

        val result = NotificationParser.parse(packageName, title, text)

        if (result.isEarnings && result.amount != null) {
            serviceScope.launch {
                try {
                    // Find the most recent active shift
                    val activeShift = database?.shiftDao()?.getActiveShift()
                    if (activeShift != null) {
                        // Find the active trip, or fall back to the most recent trip in this shift
                        val activeTrip = database?.tripDao()?.getActiveTrip(activeShift.id)
                        var tripToUpdate = activeTrip
                        if (tripToUpdate == null) {
                            val trips = database?.tripDao()?.getTripsForShift(activeShift.id)?.first()
                            tripToUpdate = trips?.lastOrNull()
                        }
                        val tripId = tripToUpdate?.id

                        if (tripId != null) {
                            // Update trip with platform and earning
                            val trip = database?.tripDao()?.getTripById(tripId)
                            trip?.let {
                                database?.tripDao()?.update(
                                    it.copy(
                                        platform = result.platform.displayName.lowercase(),
                                        earningInr = result.amount,
                                        earningRawNotif = result.rawText
                                    )
                                )
                            }

                            // Also insert into earnings table
                            database?.earningDao()?.insert(
                                Earning(
                                    tripId = tripId,
                                    amountInr = result.amount,
                                    source = "notification",
                                    timestamp = System.currentTimeMillis(),
                                    platform = result.platform.displayName.lowercase()
                                )
                            )

                            Log.i(TAG, "Logged earning: ₹${result.amount} from ${result.platform.displayName}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification earning", e)
                }
            }
        }

        if (result.isNewOrder) {
            Log.i(TAG, "New order detected from ${result.platform.displayName}")
            // The platform tag will be applied to the next trip started by the FSM
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
