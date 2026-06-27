package com.gigrun.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gigrun.R
import com.gigrun.core.utils.HaversineCalculator
import com.gigrun.core.utils.PolylineEncoder
import com.gigrun.core.utils.RidingScoreService
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.entities.Shift
import com.gigrun.data.database.entities.Trip
import com.gigrun.data.preferences.UserPreferences
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Persistent foreground service that handles:
 * - GPS location tracking with adaptive polling rates
 * - FSM state machine transitions
 * - Trip recording and distance accumulation
 * - Battery temperature monitoring for thermal throttling
 * - WakeLock management
 */
class LocationTrackingService : Service() {

    companion object {
        const val TAG = "LocationTrackingService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "gigrun_tracking"
        const val ACTION_START = "com.gigrun.START_TRACKING"
        const val ACTION_STOP = "com.gigrun.STOP_TRACKING"

        private const val FAST_INTERVAL_MS = 5_000L
        private const val SLOW_INTERVAL_MS = 60_000L
        private const val THERMAL_THRESHOLD_CELSIUS = 43.0
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var userPreferences: UserPreferences
    private lateinit var database: AppDatabase
    private lateinit var wakeLock: PowerManager.WakeLock

    private val fsmEngine = FsmEngine()
    private val ridingScoreService = RidingScoreService()
    private lateinit var speedAlertService: SpeedAlertService

    private var currentShiftId: Long? = null
    private var currentTripId: Long? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var tripPathPoints = mutableListOf<Pair<Double, Double>>()
    private var accumulatedTripDistance = 0.0
    private var waitStartTime: Long? = null
    private var isThermalThrottled = false
    private var currentIntervalMs = FAST_INTERVAL_MS

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0) ?: return
            val wasThrottled = isThermalThrottled
            isThermalThrottled = temp >= THERMAL_THRESHOLD_CELSIUS
            if (isThermalThrottled && !wasThrottled) {
                Log.w(TAG, "Battery temp $temp°C — throttling GPS to slow mode")
                updateLocationInterval(SLOW_INTERVAL_MS)
            } else if (!isThermalThrottled && wasThrottled) {
                Log.i(TAG, "Battery temp $temp°C — restoring GPS interval")
                updateLocationIntervalForState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        userPreferences = UserPreferences(this)
        database = AppDatabase.getInstance(applicationContext)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GigRun::TrackingWakeLock")

        speedAlertService = SpeedAlertService(this)

        createNotificationChannel()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    serviceScope.launch {
                        processLocation(location.latitude, location.longitude, location.speed)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting tracking..."))
                if (!wakeLock.isHeld) wakeLock.acquire(8 * 60 * 60 * 1000L) // 8 hours max
                fsmEngine.reset()
                ridingScoreService.start(getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager)
                serviceScope.launch {
                    initializeAnchors()
                    userPreferences.speedAlertEnabled.first().let { enabled ->
                        speedAlertService.isEnabled = enabled
                    }
                    userPreferences.speedLimit.first().let { limit ->
                        speedAlertService.speedLimit = limit
                    }
                }
                startLocationUpdates()
                serviceScope.launch { startShift() }
            }
        }
        return START_STICKY
    }

    private suspend fun initializeAnchors() {
        userPreferences.homeAnchor.first()?.let { (lat, lon, radius) ->
            fsmEngine.homeAnchor = FsmEngine.AnchorPoint(lat, lon, radius)
        }
        userPreferences.storeAnchor.first()?.let { (lat, lon, radius) ->
            fsmEngine.storeAnchor = FsmEngine.AnchorPoint(lat, lon, radius)
        }
        userPreferences.collegeAnchor.first()?.let { (lat, lon, radius) ->
            fsmEngine.collegeAnchor = FsmEngine.AnchorPoint(lat, lon, radius)
        }
    }

    private suspend fun startShift() {
        val existingShift = database.shiftDao().getActiveShift()
        if (existingShift != null) {
            currentShiftId = existingShift.id
        } else {
            val shift = Shift(startTime = System.currentTimeMillis())
            currentShiftId = database.shiftDao().insert(shift)
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FAST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private suspend fun processLocation(lat: Double, lon: Double, speed: Float) {
        val result = fsmEngine.processLocation(lat, lon)
        val speedKmh = speed * 3.6
        speedAlertService.checkSpeed(speedKmh)
        ridingScoreService.updateSpeed(speedKmh)

        // Accumulate distance
        lastLat?.let { pLat ->
            lastLon?.let { pLon ->
                val dist = HaversineCalculator.distanceInKm(pLat, pLon, lat, lon)
                if (dist < 0.5) { // Filter out GPS jumps > 500m
                    accumulatedTripDistance += dist
                    userPreferences.addDistance(dist)
                }
            }
        }
        lastLat = lat
        lastLon = lon

        // Add to current trip path
        if (fsmEngine.currentState == FsmEngine.State.DELIVERING_ORDER ||
            fsmEngine.currentState == FsmEngine.State.UNCLASSIFIED_COMMUTE) {
            tripPathPoints.add(Pair(lat, lon))
        }

        if (result.changed) {
            handleStateTransition(result, lat, lon)
        }

        // Update notification with current state
        updateNotification("${fsmEngine.currentState.name} | ${String.format("%.1f", accumulatedTripDistance)} km")

        // Adjust GPS interval based on state (unless thermally throttled)
        if (!isThermalThrottled) {
            updateLocationIntervalForState()
        }
    }

    private suspend fun handleStateTransition(result: FsmEngine.TransitionResult, lat: Double, lon: Double) {
        val shiftId = currentShiftId ?: return

        when (result.newState) {
            FsmEngine.State.WAITING_AT_STORE -> {
                // End any active trip
                finishCurrentTrip(lat, lon)
                // Start wait timer
                waitStartTime = System.currentTimeMillis()
            }

            FsmEngine.State.DELIVERING_ORDER -> {
                // Start a new trip
                val waitSec = waitStartTime?.let {
                    ((System.currentTimeMillis() - it) / 1000).toInt()
                } ?: 0
                waitStartTime = null

                val trip = Trip(
                    shiftId = shiftId,
                    startTime = System.currentTimeMillis(),
                    startLat = lat,
                    startLon = lon,
                    waitTimeSec = waitSec
                )
                currentTripId = database.tripDao().insert(trip)
                accumulatedTripDistance = 0.0
                tripPathPoints.clear()
                tripPathPoints.add(Pair(lat, lon))
            }

            FsmEngine.State.ORDER_COMPLETE -> {
                finishCurrentTrip(lat, lon)
            }

            FsmEngine.State.UNCLASSIFIED_COMMUTE -> {
                if (result.previousState == FsmEngine.State.IDLE_AT_HOME) {
                    // Start a commute trip
                    val trip = Trip(
                        shiftId = shiftId,
                        platform = "commute",
                        startTime = System.currentTimeMillis(),
                        startLat = lat,
                        startLon = lon
                    )
                    currentTripId = database.tripDao().insert(trip)
                    accumulatedTripDistance = 0.0
                    tripPathPoints.clear()
                    tripPathPoints.add(Pair(lat, lon))
                }
            }

            FsmEngine.State.AT_COLLEGE -> {
                finishCurrentTrip(lat, lon)
            }

            FsmEngine.State.IDLE_AT_HOME -> {
                finishCurrentTrip(lat, lon)
            }

            else -> {}
        }
    }

    private suspend fun finishCurrentTrip(lat: Double, lon: Double) {
        val tripId = currentTripId ?: return
        val trip = database.tripDao().getTripById(tripId) ?: return

        val encodedPath = if (tripPathPoints.size > 1) {
            PolylineEncoder.encode(tripPathPoints)
        } else null

        database.tripDao().update(
            trip.copy(
                endTime = System.currentTimeMillis(),
                endLat = lat,
                endLon = lon,
                distanceKm = accumulatedTripDistance,
                pathEncoded = encodedPath
            )
        )
        currentTripId = null
        tripPathPoints.clear()
        accumulatedTripDistance = 0.0
    }

    private fun updateLocationIntervalForState() {
        val desiredInterval = when (fsmEngine.currentState) {
            FsmEngine.State.DELIVERING_ORDER,
            FsmEngine.State.UNCLASSIFIED_COMMUTE -> FAST_INTERVAL_MS
            else -> SLOW_INTERVAL_MS
        }
        if (desiredInterval != currentIntervalMs) {
            updateLocationInterval(desiredInterval)
        }
    }

    @Suppress("MissingPermission")
    private fun updateLocationInterval(intervalMs: Long) {
        currentIntervalMs = intervalMs
        fusedLocationClient.removeLocationUpdates(locationCallback)
        val priority = if (intervalMs <= FAST_INTERVAL_MS) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GigRun Tracking")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GigRun Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active shift tracking"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        runBlocking {
            withContext(Dispatchers.IO) {
                finishCurrentTrip(lastLat ?: 0.0, lastLon ?: 0.0)
                currentShiftId?.let { id ->
                    database.shiftDao().getShiftById(id)?.let { shift ->
                        database.shiftDao().update(shift.copy(endTime = System.currentTimeMillis()))
                    }
                }
            }
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        if (wakeLock.isHeld) wakeLock.release()
        ridingScoreService.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
