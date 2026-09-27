package com.gigrun.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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

    // Crash detection state (written on location/sensor threads, read on IO)
    @Volatile private var spikeDetectedTime: Long? = null
    @Volatile private var lastHighVelocityTime: Long? = null
    @Volatile private var velocityDropDetected = false
    @Volatile private var stillnessStartTime: Long? = null
    @Volatile private var isStill = false
    @Volatile private var lastKnownLat: Double? = null
    @Volatile private var lastKnownLon: Double? = null
    @Volatile private var lastSpeed: Float = 0f

    // Countdown state
    private var countdownJob: Job? = null
    @Volatile private var isCountdownActive = false
    private var locationCallback: LocationCallback? = null
    private var alarmRingtone: Ringtone? = null

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
        // Re-read the threshold on every start: Settings can change it (or clamp
        // it) while the monitor runs — never evaluate spikes against a stale G.
        serviceScope.launch {
            try { gForceThreshold = userPreferences.gForceThreshold.first() } catch (_: Exception) {}
        }
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

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        startAccelerometerMonitoring()
        startLocationMonitoring()

        return START_STICKY
    }

    private fun startAccelerometerMonitoring() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w(TAG, "no accelerometer on this device — crash detection inactive")
            return
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun hasLocationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun startLocationMonitoring() {
        // Never request GPS without runtime permission — revoked permission while the
        // monitor runs used to throw SecurityException on the main thread.
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission missing — crash GPS monitoring paused")
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    if (loc.isFromMockProvider) return
                    lastKnownLat = loc.latitude
                    lastKnownLon = loc.longitude
                    val currentSpeed = loc.speed * 3.6f // m/s to km/h

                    // Track velocity for sudden-stop detection
                    if (currentSpeed > VELOCITY_HIGH_THRESHOLD_KMH) {
                        lastHighVelocityTime = System.currentTimeMillis()
                    }

                    // Check for velocity drop (local copies — fields mutate on sensor thread)
                    val spikeT = spikeDetectedTime
                    val highV = lastHighVelocityTime
                    if (spikeT != null && highV != null) {
                        val timeSinceSpike = System.currentTimeMillis() - spikeT
                        if (timeSinceSpike <= SPIKE_WINDOW_MS && currentSpeed < VELOCITY_LOW_THRESHOLD_KMH) {
                            velocityDropDetected = true
                        }
                    }

                    // Stillness detection
                    if (currentSpeed < 2.0) {
                        val stillT = stillnessStartTime ?: System.currentTimeMillis().also { stillnessStartTime = it }
                        val stillDuration = System.currentTimeMillis() - stillT
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
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, mainLooper)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        if (isCountdownActive) return
        val values = event.values
        if (values == null || values.size < 3) return

        val x = values[0]
        val y = values[1]
        val z = values[2]
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

    private fun getVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) { null }
    }

    private fun triggerCrashCountdown() {
        if (isCountdownActive) return
        isCountdownActive = true

        // Sound alarm (R&D fix: keep reference so cancel/stop actually silences it)
        try {
            stopAlarm()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            alarmRingtone = RingtoneManager.getRingtone(this, alarmUri)
            alarmRingtone?.play()
        } catch (_: Exception) {}

        // Vibrate (R&D fix: compat + hasVibrator guard)
        try {
            val vibrator = getVibrator()
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500), 0)
                }
            }
        } catch (_: Exception) {}

        // Start countdown
        countdownJob = serviceScope.launch {
            try {
                for (i in COUNTDOWN_SECONDS downTo 1) {
                    updateCountdownNotification(i)
                    delay(1000L)
                }
                // Countdown finished — send emergency SMS
                if (!isTestMode) {
                    sendEmergencySms()
                }
            } finally {
                stopAlarm()
                isCountdownActive = false
                isTestMode = false
                resetCrashState()
            }
        }
    }

    private fun stopAlarm() {
        try { alarmRingtone?.stop() } catch (_: Exception) {}
        alarmRingtone = null
        try { getVibrator()?.cancel() } catch (_: Exception) {}
    }

    private fun cancelCountdown() {
        val wasTest = isTestMode
        countdownJob?.cancel()
        countdownJob = null
        isCountdownActive = false
        isTestMode = false
        resetCrashState()
        stopAlarm()
        // Test-mode runs have no shift to protect — don't leave a 1 s GPS
        // foreground service running forever after the demo.
        if (wasTest) stopSelf()

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
        // R&D fix: runtime SEND_SMS guard (suppressed lint is not a runtime check).
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS not granted — skipping emergency SMS")
            return
        }
        // Re-validate at send time: a rooted prefs edit bypasses the setter.
        val contacts = userPreferences.emergencyContacts.first()
            .filter { it.isNotBlank() && com.gigrun.core.utils.PrefsValidation.isSafeContact(it) }
        if (contacts.isEmpty()) {
            Log.w(TAG, "No valid emergency contacts — skipping SMS")
            return
        }
        // Dispatch cooldown: stop-and-go traffic must not SMS every red light.
        val now0 = System.currentTimeMillis()
        if (now0 - userPreferences.getLastCrashSms() < 10 * 60_000L) {
            Log.w(TAG, "Crash SMS cooldown active — skipping duplicate dispatch")
            return
        }
        val lat = lastKnownLat
        val lon = lastKnownLon
        val locationPart = if (lat != null && lon != null) {
            "Last known location: https://maps.google.com/?q=$lat,$lon "
        } else {
            "Last known location: unknown (no GPS fix yet). "
        }
        val message = "EMERGENCY — GigRun detected a possible crash. " +
                locationPart +
                "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

        try {
            // R&D fix: API 28–30 compat (getSystemService(SmsManager) is API 31+).
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            for (contact in contacts) {
                try {
                    // Split long URL SMS into parts to avoid truncation.
                    val parts = smsManager.divideMessage(message)
                    if (parts.size <= 1) {
                        smsManager.sendTextMessage(contact, null, message, null, null)
                    } else {
                        smsManager.sendMultipartTextMessage(contact, null, parts, null, null)
                    }
                    // Number redacted: contact PII stays out of logcat.
                    Log.i(TAG, "Emergency SMS sent")
                } catch (e: Exception) { Log.e(TAG, "SMS send failed", e) }
            }
            // Cooldown starts on dispatch attempt, not per-contact success.
            userPreferences.noteCrashSms(now0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency SMS", e)
        } finally {
            // R&D fix: stop repeating vibration after dispatch.
            try { getVibrator()?.cancel() } catch (_: Exception) {}
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
        countdownJob?.cancel()
        countdownJob = null
        stopAlarm()
        sensorManager.unregisterListener(this)
        locationCallback?.let { try { fusedLocationClient.removeLocationUpdates(it) } catch (_: Exception) {} }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
