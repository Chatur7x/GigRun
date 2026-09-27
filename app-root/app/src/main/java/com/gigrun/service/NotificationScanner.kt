package com.gigrun.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gigrun.core.utils.NotificationParser
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.entities.Earning
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

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
            "com.ubercab.driver",
            "com.swiggy.delivery",
            "com.swiggy.driver",
            "com.zomato.delivery",
            "com.zomato.driver",
            "com.bigbasket.delivery",
            "com.porter.delivery",
            "com.porter.driver",
            "com.dunzo.delivery",
            "com.dunzo.driver",
            "com.amazon.flex"
        )

        // R&D fix: static signal bridge — NotificationListenerService is system-managed
        // and can't Hilt-inject SmartPlatformDetector, so expose the last signal statically.
        @Volatile var lastPlatformSignal: String? = null
        @Volatile var lastPlatformSignalTime: Long = 0L
    }

    private fun detectorHint(platformDisplayName: String) {
        lastPlatformSignal = platformDisplayName
        lastPlatformSignalTime = System.currentTimeMillis()
        // Also refresh the Hilt singleton cache if it exists (best-effort, no crash).
        try {
            com.gigrun.core.utils.SmartPlatformDetectorBridge.onNotification(platformDisplayName)
        } catch (_: Exception) {}
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: AppDatabase? = null

    override fun onCreate() {
        super.onCreate()
        try {
            database = AppDatabase.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "database init failed — notifications will be ignored", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName

        if (packageName !in MONITORED_PACKAGES) return

        val extras = notification.notification.extras
        // R&D fix: also read bigText/subText/messages — title/text alone truncates fares.
        val title = extras.getCharSequence("android.title")?.toString()
        var text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
            ?: extras.getCharSequence("android.subText")?.toString()
        // MessagingStyle (chat-style delivery updates) carries fares in android.messages.
        if (text.isNullOrBlank()) {
            text = try {
                val msgs = extras.getParcelableArray("android.messages")
                msgs?.mapNotNull {
                    @Suppress("DEPRECATION")
                    val m = it as? android.app.Notification.MessagingStyle.Message
                    listOfNotNull(m?.text?.toString()).joinToString(" ")
                }?.joinToString(" ")?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }

        // Logcat hygiene: platform only — titles/texts/fares stay out of logs.
        Log.d(TAG, "Notification from $packageName")

        val result = NotificationParser.parse(packageName, title, text)
        // Feed the smart-detector signal (was dead code — no caller of onNotification).
        try { detectorHint(result.platform.displayName) } catch (_: Exception) {}

        // P0 guard: a promo/rogue push claiming ₹99999 must never auto-commit.
        // Indian gig fares live well under ₹20k — quarantine the absurd.
        val amount = result.amount
        if (result.isEarnings && amount != null) {
            if (amount !in 1.0..20_000.0) {
                Log.w(TAG, "Quarantined out-of-range earning ₹$amount from $packageName")
                return
            }
            serviceScope.launch {
                try {
                    // Find the most recent active shift
                    val activeShift = database?.shiftDao()?.getActiveShift()
                    if (activeShift != null) {
                        // Newest trip in one indexed query — no full-shift blob load.
                        val tripToUpdate = database?.tripDao()?.getActiveTrip(activeShift.id)
                            ?: database?.tripDao()?.getLatestTripForShift(activeShift.id)
                        val tripId = tripToUpdate?.id

                        if (tripId != null) {
                            // Update trip with platform and earning + surge/bonus/tips
                            // R&D fix: canonical platform case — OcrProcessor uses "Uber",
                            // old code wrote lowercase and split GROUP BY stats.
                            val canonicalPlatform = result.platform.displayName
                            val trip = database?.tripDao()?.getTripById(tripId)
                            trip?.let {
                                // R&D fix: only overwrite platform when the stored one is
                                // untagged/commute — never clobber a driver-confirmed tag with a
                                // late/previous-trip notification (race vs FSM trip creation).
                                val shouldRetag = it.platform.equals("untagged", true) ||
                                        it.platform.equals("commute", true) ||
                                        it.platform.equals(canonicalPlatform, true)
                                if (!shouldRetag) {
                                    // Wrong-trip notification (e.g. Blinkit promo landing on
                                    // a confirmed Uber trip): touch NOTHING — no fare
                                    // rewrite, no earnings row, no raw-text clobber.
                                    Log.i(TAG, "Ignoring cross-trip notification for trip $tripId")
                                    return@let
                                }
                                // Dedup: re-posted/updated notifications carry identical raw
                                // text — never double-count the fare or rewrite amounts.
                                // Normalized: whitespace-only deltas ("earned ₹52" vs
                                // "earned  ₹52\n") are the same notification re-posted.
                                fun norm(s: String) = s.trim().replace("\\s+".toRegex(), " ")
                                val isDuplicate = it.earningRawNotif?.let { prev -> norm(prev) == norm(result.rawText) } ?: false
                                // Window dedup: same fare on this trip within the hour is a
                                // re-post with mutated punctuation/counters ("Delivered
                                // 2/3…₹52") — the one-slot text compare can't see it.
                                val recentSame = try {
                                    database?.earningDao()?.countSimilar(
                                        tripId, result.amount,
                                        System.currentTimeMillis() - 3_600_000L
                                    ) ?: 0
                                } catch (_: Exception) { 0 }
                                if (isDuplicate || recentSame > 0) return@let
                                // Velocity cap: a promo loop / rogue SDK spraying
                                // just-under-cap pushes must not mint lakhs per day.
                                try {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                    cal.set(java.util.Calendar.MILLISECOND, 0)
                                    val dayStart = cal.timeInMillis
                                    val dayTotal = database?.earningDao()
                                        ?.getTotalEarningsForDay(dayStart, dayStart + 86_400_000L) ?: 0.0
                                    if (dayTotal + result.amount > 100_000.0) {
                                        Log.w(TAG, "Quarantined: daily notification earnings would exceed ₹1L")
                                        return@let
                                    }
                                } catch (_: Exception) { }
                                val updated = it.copy(
                                    // shouldRetag is true here (early return above):
                                    // this fare belongs to this trip.
                                    platform = canonicalPlatform,
                                    earningInr = result.amount,
                                    // Cap stored raw text: unbounded notification bodies bloat rows.
                                    // 2000 chars keeps full re-posts comparable (dedup above).
                                    earningRawNotif = result.rawText.take(2000),
                                    baseFare = result.baseFare,
                                    surgeAmount = result.surgeAmount,
                                    bonusAmount = result.bonusAmount,
                                    tipAmount = result.tipAmount,
                                    surgeReason = result.surgeReason,
                                    isSurgeTrip = result.surgeAmount != null || result.bonusAmount != null
                                )
                                val earning = Earning(
                                    tripId = tripId,
                                    amountInr = result.amount,
                                    source = "notification",
                                    timestamp = System.currentTimeMillis(),
                                    platform = canonicalPlatform
                                )
                                // Atomic: FSM trip creation can't interleave between the two.
                                try {
                                    val db = database
                                    if (db != null) db.tripDao().attachEarning(updated, db.earningDao(), earning)
                                } catch (e: Exception) {
                                    Log.e(TAG, "attachEarning failed", e)
                                }
                            }

                            Log.i(TAG, "Logged earning from ${result.platform.displayName}")
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
