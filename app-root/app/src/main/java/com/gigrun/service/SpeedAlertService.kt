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
    var isEnabled: Boolean = true

    private var lastAlertTime = 0L

    fun checkSpeed(speedKmh: Double) {
        if (!isEnabled) return
        if (speedKmh <= speedLimit) return

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
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    fun resetAlert() {
        _alertState.value = SpeedAlert(speedLimit = speedLimit)
    }
}
