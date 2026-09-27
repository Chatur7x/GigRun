package com.gigrun.core.utils

import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.preferences.UserPreferences
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class DetectionResult(
    val platform: String,
    val confidence: Float,
    val signals: List<String>
)

@Singleton
class SmartPlatformDetector @Inject constructor(
    private val tripDao: TripDao,
    private val prefs: UserPreferences
) {
    // Recent notification cache — set by NotificationScanner
    @Volatile var lastNotificationPlatform: String? = null
    @Volatile var lastNotificationTime: Long = 0L

    fun onNotification(platform: String) {
        lastNotificationPlatform = platform
        lastNotificationTime = System.currentTimeMillis()
    }

    suspend fun detect(currentLocation: LatLng? = null): DetectionResult {
        val signals = mutableListOf<Pair<String, Float>>()
        val now = System.currentTimeMillis()

        // Signal 1: Recent notification within 60s (weight 0.50)
        // R&D fix: also consult the static bridge — the system-managed
        // NotificationScanner can't inject this Hilt singleton directly.
        val staticPlatform = SmartPlatformDetectorBridge.lastPlatform
        val staticTime = SmartPlatformDetectorBridge.lastTime
        val effectivePlatform = lastNotificationPlatform ?: staticPlatform
        val effectiveTime = if (lastNotificationPlatform != null) lastNotificationTime else staticTime
        effectivePlatform?.let { p ->
            val age = now - effectiveTime
            if (age < 60_000) {
                val weight = 0.50f * (1f - age / 60_000f * 0.3f) // decay slightly
                signals.add("notification:$p" to weight)
            }
        }

        // Signal 2: Most frequent platform in last 7 days (weight 0.25)
        try {
            val weekAgo = now - 7 * 86_400_000L
            val stats = tripDao.getPlatformStats(weekAgo)
            stats.maxByOrNull { it.tripCount }?.let { top ->
                signals.add("history:${top.platform}" to 0.25f)
            }
        } catch (_: Exception) {}

        // Signal 3: Time-of-day pattern — most common platform at this hour (weight 0.15)
        // Simplified: if no history, no signal. Could expand with hour bucket query.

        // Signal 4: Enabled platforms — if only one enabled, boost it (weight 0.10)
        try {
            val enabled = prefs.enabledPlatforms.first()
            if (enabled.size == 1) {
                enabled.firstOrNull()?.let { signals.add("single:$it" to 0.10f) }
            }
        } catch (_: Exception) {}

        if (signals.isEmpty()) return DetectionResult("untagged", 0f, emptyList())

        // Weighted vote (R&D fix: substringAfter avoids IndexOutOfBounds on colon-less signals;
        // lowercase grouping so "Uber" vs "uber" never splits the vote).
        val grouped = signals.groupBy { it.first.substringAfter(":", it.first).lowercase(java.util.Locale.ROOT) }
            .mapValues { (_, v) -> v.sumOf { it.second.toDouble() }.toFloat() }
        val winner = grouped.maxByOrNull { it.value } ?: return DetectionResult("untagged", 0f, emptyList())
        val totalWeight = signals.sumOf { it.second.toDouble() }.toFloat()
        // Evidence floor: a lone weak signal (one old trip) must not report 1.0.
        val rawConfidence = (winner.value / totalWeight).coerceIn(0f, 1f)
        val evidence = (totalWeight / 0.85f).coerceIn(0f, 1f)
        val confidence = (rawConfidence * evidence).coerceIn(0f, 1f)

        return DetectionResult(winner.key, confidence, signals.map { it.first })
    }
}

/**
 * Static bridge for NotificationScanner (system service, no Hilt) → SmartPlatformDetector.
 * R&D fix for dead onNotification signal.
 */
object SmartPlatformDetectorBridge {
    @Volatile var lastPlatform: String? = null
    @Volatile var lastTime: Long = 0L
    fun onNotification(platform: String) {
        lastPlatform = platform
        lastTime = System.currentTimeMillis()
    }
}
