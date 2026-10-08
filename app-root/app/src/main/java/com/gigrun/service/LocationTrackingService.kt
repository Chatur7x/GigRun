package com.gigrun.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.sync.withLock

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
    // Dedicated flush scope: survives serviceScope.cancel() in onDestroy so the
    // final trip/shift write always lands (old code launched into the scope it
    // cancelled on the next line — flush never ran).
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    @Volatile private var isThermalThrottled = false
    private var currentIntervalMs = FAST_INTERVAL_MS
    private var lastFgUpdateMs = 0L
    private var lastFgState: FsmEngine.State? = null
    private val stateMutex = kotlinx.coroutines.sync.Mutex()
    private var pendingDistanceKm = 0.0
    private var lastDistanceFlushMs = 0L

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Mock-location guard: rooted/dev-options fake GPS feeding
                    // 0.49km fixes would mint fraudulent distance and trips.
                    if (location.isFromMockProvider) {
                        Log.w(TAG, "dropping mocked location fix")
                        return
                    }
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
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("Starting tracking..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
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
        // Close stale open trips orphaned by process death — otherwise they stay
        // open forever and earnings attach to the wrong trip.
        try {
            val now = System.currentTimeMillis()
            database.tripDao().getAllOpenTrips().forEach { open ->
                database.tripDao().update(open.copy(endTime = open.endTime ?: now))
            }
            currentTripId = null
        } catch (e: Exception) { Log.w(TAG, "stale trip close failed", e) }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission revoked — pausing GPS updates")
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FAST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private suspend fun processLocation(lat: Double, lon: Double, speed: Float) {
        // R&D fix: serialize GPS processing — no overlapping FSM/trip mutations.
        stateMutex.withLock {
            processLocationLocked(lat, lon, speed)
        }
    }

    private suspend fun processLocationLocked(lat: Double, lon: Double, speed: Float) {
        val result = fsmEngine.processLocation(lat, lon)
        val speedKmh = speed * 3.6
        speedAlertService.checkSpeed(speedKmh)
        ridingScoreService.updateSpeed(speedKmh)

        // Accumulate distance (R&D fix: batch DataStore writes — flush ≤1/min or ≥0.5km)
        lastLat?.let { pLat ->
            lastLon?.let { pLon ->
                val dist = HaversineCalculator.distanceInKm(pLat, pLon, lat, lon)
                if (dist < 0.5) { // Filter out GPS jumps > 500m
                    accumulatedTripDistance += dist
                    pendingDistanceKm += dist
                    val now = System.currentTimeMillis()
                    if (pendingDistanceKm >= 0.5 || now - lastDistanceFlushMs >= 60_000L) {
                        val flush = pendingDistanceKm
                        pendingDistanceKm = 0.0
                        lastDistanceFlushMs = now
                        try { userPreferences.addDistance(flush) } catch (e: Exception) { Log.w(TAG, "distance flush failed", e) }
                    }
                }
            }
        }
        lastLat = lat
        lastLon = lon

        // Add to current trip path (capped: a 30-min customer wait at 5 s/fix would
        // otherwise grow this list unbounded until encode -> OOM spike).
        if (fsmEngine.currentState == FsmEngine.State.DELIVERING_ORDER ||
            fsmEngine.currentState == FsmEngine.State.UNCLASSIFIED_COMMUTE) {
            if (tripPathPoints.size >= 2000) tripPathPoints.removeAt(0)
            tripPathPoints.add(Pair(lat, lon))
        }

        if (result.changed) {
            handleStateTransition(result, lat, lon)
        }

        // Feature 34 — heat index check on the same 30-second cadence as the
        // notification tick. Cached network fetch (once/hour), no new service.
        val nowFx = System.currentTimeMillis()
        if (fsmEngine.currentState != lastFgState || nowFx - lastFgUpdateMs >= 30_000L) {
            serviceScope.launch {
                try { HeatIndexChecker.checkAndNotify(this@LocationTrackingService, lat, lon) }
                catch (_: Exception) { /* network/location issues — not fatal */ }
            }
        }

        // Update notification with current state — throttled: every fix re-issuing
        // startForeground is IPC + shade churn. Refresh on state change or 30 s.
        val nowFg = System.currentTimeMillis()
        if (fsmEngine.currentState != lastFgState || nowFg - lastFgUpdateMs >= 30_000L) {
            lastFgState = fsmEngine.currentState
            lastFgUpdateMs = nowFg
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification("${fsmEngine.currentState.name} | ${String.format("%.1f", accumulatedTripDistance)} km"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } catch (e: Exception) { Log.w(TAG, "foreground update failed", e) }
        }

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
                // Start the wait clock here too: the transient collapses to
                // WAITING_AT_STORE on the next fix, which may be 60 s away.
                if (waitStartTime == null) waitStartTime = System.currentTimeMillis()
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
        if (!hasLocationPermission()) return
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
        // Kept for compat; foreground updates go through ServiceCompat.startForeground above.
        try {
            val notification = buildNotification(text)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) { Log.w(TAG, "notify failed", e) }
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
        // Flush in a scope that survives this destroy + NonCancellable + mutex,
        // so distance/trip/shift writes cannot be cancelled mid-flight.
        // Never runBlocking on the main thread.
        val flushKm = pendingDistanceKm
        val snapshotTripId = currentTripId
        val snapshotShiftId = currentShiftId
        val snapshotLat = lastLat
        val snapshotLon = lastLon
        val snapshotPath = tripPathPoints.toList()
        val snapshotDist = accumulatedTripDistance
        flushScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    stateMutex.withLock { flushLocked(flushKm, snapshotTripId, snapshotShiftId, snapshotLat, snapshotLon, snapshotPath, snapshotDist) }
                }
            } catch (e: Exception) { Log.w(TAG, "destroy flush failed", e) }
        }
        try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { if (wakeLock.isHeld) wakeLock.release() } catch (_: Exception) {}
        ridingScoreService.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun flushLocked(flushKm: Double, tripId: Long?, shiftId: Long?, lat: Double?, lon: Double?, path: List<Pair<Double, Double>>, dist: Double) {
        try {
            if (flushKm > 0) userPreferences.addDistance(flushKm)
        } catch (e: Exception) { Log.w(TAG, "distance flush failed", e) }
        try {
            if (tripId != null && lat != null && lon != null) {
                val trip = database.tripDao().getTripById(tripId)
                if (trip != null) {
                    val encodedPath = if (path.size > 1) PolylineEncoder.encode(path) else null
                    database.tripDao().update(trip.copy(endTime = System.currentTimeMillis(), endLat = lat, endLon = lon, distanceKm = dist, pathEncoded = encodedPath))
                }
            }
        } catch (e: Exception) { Log.w(TAG, "destroy trip flush failed", e) }
        try {
            if (shiftId != null) {
                val shift = database.shiftDao().getShiftById(shiftId)
                if (shift != null && shift.endTime == null) {
                    database.shiftDao().update(shift.copy(endTime = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) { Log.w(TAG, "destroy shift flush failed", e) }
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
