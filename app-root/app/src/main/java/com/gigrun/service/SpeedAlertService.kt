package com.gigrun.service

import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeedAlert(
    val speedKmh: Double = 0.0,
    val speedLimit: Double = 80.0,
    val isAlerting: Boolean = false,
    val timestamp: Long = 0L
)

class SpeedAlertService(private val context: Context) {

    companion object {
        private const val COOLDOWN_MS = 10_000L
    }

    private val _alertState = MutableStateFlow(SpeedAlert())
    val alertState: StateFlow<SpeedAlert> = _alertState.asStateFlow()

    var speedLimit: Double = 80.0
    @Volatile var isEnabled: Boolean = true

    private var lastAlertTime = 0L
    @Volatile private var lastWasOver = false

    fun checkSpeed(speedKmh: Double) {
        if (!isEnabled) return
        if (speedKmh <= speedLimit) {
            lastWasOver = false
            // R&D fix: auto-clear stale alert when back under limit.
            if (_alertState.value.isAlerting) resetAlert()
            return
        }
        // GPS-glitch filter: a single multipath spike (e.g. 250 km/h under a
        // flyover) must not beep — require two consecutive over-limit fixes.
        if (!lastWasOver) {
            lastWasOver = true
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < COOLDOWN_MS) return

        lastAlertTime = now
        _alertState.value = SpeedAlert(
            speedKmh = speedKmh,
            speedLimit = speedLimit,
            isAlerting = true,
            timestamp = now
        )

        triggerAlert()
    }

    private fun triggerAlert() {
        // R&D fix: VibratorManager compat (API 31+) + hasVibrator guard + audible beep.
        try {
            val vibrator: Vibrator? = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Exception) { null }
            if (vibrator?.hasVibrator() == true) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(300)
                }
            }
        } catch (_: Exception) {}
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {}
    }

    fun resetAlert() {
        _alertState.value = SpeedAlert(speedLimit = speedLimit)
    }
}
