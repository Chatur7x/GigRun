package com.gigrun.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gigrun.data.preferences.UserPreferences
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

/**
 * Crash detection service using multi-condition false-positive suppression.
 *
 * All THREE conditions must be true within a 4-second window:
 * 1. Accelerometer spike > configurable G-force threshold (default 4G)
 * 2. GPS velocity drops from >15 km/h to <5 km/h within 4 seconds
 * 3. Phone remains stationary for 8 seconds after spike
 *
 * If triggered: 30-second countdown with alarm.
 * If not cancelled: SMS with GPS coordinates to emergency contacts.
 */
class CrashDetectionService : Service(), SensorEventListener {

    companion object {
        const val TAG = "CrashDetector"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "gigrun_crash"
        const val ACTION_CANCEL_COUNTDOWN = "com.gigrun.CANCEL_CRASH"
        const val ACTION_TEST_MODE = "com.gigrun.TEST_CRASH"

        private const val GRAVITY = 9.81f
        private const val COUNTDOWN_SECONDS = 30
        private const val VELOCITY_HIGH_THRESHOLD_KMH = 15.0
        private const val VELOCITY_LOW_THRESHOLD_KMH = 5.0
        private const val STILLNESS_DURATION_MS = 8_000L
        private const val SPIKE_WINDOW_MS = 4_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userPreferences: UserPreferences

    private var gForceThreshold = 4.0
    private var isTestMode = false

    // Crash detection state
    private var spikeDetectedTime: Long? = null
    private var lastHighVelocityTime: Long? = null
    private var velocityDropDetected = false
    private var stillnessStartTime: Long? = null
    private var isStill = false
    private var lastKnownLat: Double? = null
    private var lastKnownLon: Double? = null
    private var lastSpeed: Float = 0f

    // Countdown state
    private var countdownJob: Job? = null
    private var isCountdownActive = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        userPreferences = UserPreferences(this)

        createNotificationChannel()

        serviceScope.launch {
            gForceThreshold = userPreferences.gForceThreshold.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_COUNTDOWN -> {
                cancelCountdown()
                return START_NOT_STICKY
            }
            ACTION_TEST_MODE -> {
                isTestMode = true
                triggerCrashCountdown()
                return START_NOT_STICKY
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigRun Safety Monitor")
            .setContentText("Crash detection active")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startAccelerometerMonitoring()
        startLocationMonitoring()

        return START_STICKY
    }

    private fun startAccelerometerMonitoring() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationMonitoring() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L).build()
        fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    lastKnownLat = loc.latitude
                    lastKnownLon = loc.longitude
                    val currentSpeed = loc.speed * 3.6f // m/s to km/h

                    // Track velocity for sudden-stop detection
                    if (currentSpeed > VELOCITY_HIGH_THRESHOLD_KMH) {
                        lastHighVelocityTime = System.currentTimeMillis()
                    }

                    // Check for velocity drop
                    if (spikeDetectedTime != null && lastHighVelocityTime != null) {
                        val timeSinceSpike = System.currentTimeMillis() - spikeDetectedTime!!
                        if (timeSinceSpike <= SPIKE_WINDOW_MS && currentSpeed < VELOCITY_LOW_THRESHOLD_KMH) {
                            velocityDropDetected = true
                        }
                    }

                    // Stillness detection
                    if (currentSpeed < 2.0) {
                        if (stillnessStartTime == null) stillnessStartTime = System.currentTimeMillis()
                        val stillDuration = System.currentTimeMillis() - stillnessStartTime!!
                        isStill = stillDuration >= STILLNESS_DURATION_MS
                    } else {
                        stillnessStartTime = null
                        isStill = false
                    }

                    lastSpeed = currentSpeed

                    // Check all three conditions
                    checkCrashConditions()
                }
            }
        }, mainLooper)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        if (isCountdownActive) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val totalG = sqrt((x * x + y * y + z * z).toDouble()) / GRAVITY

        if (totalG > gForceThreshold) {
            spikeDetectedTime = System.currentTimeMillis()
            Log.w(TAG, "G-force spike: ${String.format("%.1f", totalG)}G")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkCrashConditions() {
        if (isCountdownActive) return
        val spikeTime = spikeDetectedTime ?: return

        val timeSinceSpike = System.currentTimeMillis() - spikeTime

        // Clear stale spike data (older than 12 seconds)
        if (timeSinceSpike > 12_000) {
            resetCrashState()
            return
        }

        // All three conditions met
        if (velocityDropDetected && isStill) {
            triggerCrashCountdown()
        }
    }

    private fun triggerCrashCountdown() {
        if (isCountdownActive) return
        isCountdownActive = true

        // Sound alarm
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.play()
        } catch (_: Exception) {}

        // Vibrate
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))

        // Start countdown
        countdownJob = serviceScope.launch {
            for (i in COUNTDOWN_SECONDS downTo 1) {
                updateCountdownNotification(i)
                delay(1000L)
            }
            // Countdown finished — send emergency SMS
            if (!isTestMode) {
                sendEmergencySms()
            }
            isCountdownActive = false
            isTestMode = false
            resetCrashState()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        isCountdownActive = false
        isTestMode = false
        resetCrashState()

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigRun Safety Monitor")
            .setContentText("Crash alert cancelled")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build())
    }

    private fun resetCrashState() {
        spikeDetectedTime = null
        velocityDropDetected = false
        stillnessStartTime = null
        isStill = false
    }

    @Suppress("MissingPermission")
    private suspend fun sendEmergencySms() {
        val contacts = userPreferences.emergencyContacts.first()
        val lat = lastKnownLat ?: 0.0
        val lon = lastKnownLon ?: 0.0
        val message = "EMERGENCY — GigRun detected a possible crash. " +
                "Last known location: https://maps.google.com/?q=$lat,$lon " +
                "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

        try {
            val smsManager = getSystemService(SmsManager::class.java)
            for (contact in contacts) {
                smsManager.sendTextMessage(contact, null, message, null, null)
                Log.i(TAG, "Emergency SMS sent to $contact")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency SMS", e)
        }
    }

    private fun updateCountdownNotification(secondsLeft: Int) {
        val cancelIntent = Intent(this, CrashDetectionService::class.java).apply {
            action = ACTION_CANCEL_COUNTDOWN
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ CRASH DETECTED")
            .setContentText(if (isTestMode) "TEST MODE — $secondsLeft seconds" else "Emergency SMS in $secondsLeft seconds")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "I'M OK — CANCEL", cancelPendingIntent)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GigRun Safety Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Crash detection and emergency alerts"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
