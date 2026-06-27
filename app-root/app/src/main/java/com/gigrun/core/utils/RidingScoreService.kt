package com.gigrun.core.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class RidingScore(
    val score: Int = 100,
    val harshBrakingCount: Int = 0,
    val harshAccelerationCount: Int = 0,
    val sharpTurnCount: Int = 0,
    val totalEvents: Int = 0
)

class RidingScoreService : SensorEventListener {

    companion object {
        private const val GRAVITY = 9.81f
        private const val BRAKING_THRESHOLD = 15.0
        private const val ACCELERATION_THRESHOLD = 12.0
        private const val TURN_THRESHOLD = 18.0
        private const val SCORE_WINDOW_MS = 60_000L
        private const val EVENT_COOLDOWN_MS = 500L
    }

    private val _score = MutableStateFlow(RidingScore())
    val score: StateFlow<RidingScore> = _score.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var isMonitoring = false
    private var lastSpeed = 0.0
    private var lastEventTime = 0L

    fun start(sensorManager: SensorManager) {
        if (isMonitoring) return
        this.sensorManager = sensorManager
        isMonitoring = true
        reset()
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        isMonitoring = false
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }

    fun updateSpeed(speedKmh: Double) {
        lastSpeed = speedKmh
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        if (!isMonitoring) return

        val now = System.currentTimeMillis()
        if (now - lastEventTime < EVENT_COOLDOWN_MS) return

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = abs(x) + abs(y) + abs(z)

        var type: String? = null
        when {
            magnitude > BRAKING_THRESHOLD && lastSpeed > 10.0 -> type = "harsh_braking"
            magnitude > ACCELERATION_THRESHOLD && y < -ACCELERATION_THRESHOLD -> type = "harsh_acceleration"
            abs(x) > TURN_THRESHOLD -> type = "sharp_turn"
        }

        if (type != null) {
            lastEventTime = now
            val current = _score.value
            _score.value = current.copy(
                harshBrakingCount = if (type == "harsh_braking") current.harshBrakingCount + 1 else current.harshBrakingCount,
                harshAccelerationCount = if (type == "harsh_acceleration") current.harshAccelerationCount + 1 else current.harshAccelerationCount,
                sharpTurnCount = if (type == "sharp_turn") current.sharpTurnCount + 1 else current.sharpTurnCount,
                totalEvents = current.totalEvents + 1,
                score = calculateScore(
                    current.harshBrakingCount + (if (type == "harsh_braking") 1 else 0),
                    current.harshAccelerationCount + (if (type == "harsh_acceleration") 1 else 0),
                    current.sharpTurnCount + (if (type == "sharp_turn") 1 else 0)
                )
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateScore(harshBraking: Int, harshAcceleration: Int, sharpTurns: Int): Int {
        var s = 100
        s -= harshBraking * 10
        s -= harshAcceleration * 8
        s -= sharpTurns * 6
        return s.coerceIn(0, 100)
    }

    fun reset() {
        _score.value = RidingScore()
    }

    fun getRidingTip(): String {
        val s = _score.value.score
        return when {
            s >= 90 -> "Excellent riding! Very smooth."
            s >= 70 -> "Good riding. Minor improvements can help."
            s >= 50 -> "Moderate riding. Try to reduce harsh braking."
            else -> "Needs improvement. Ride more smoothly for better efficiency."
        }
    }
}
