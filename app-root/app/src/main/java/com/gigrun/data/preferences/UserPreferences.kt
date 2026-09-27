package com.gigrun.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.gigrun.core.utils.PrefsValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gigrun_settings")

// Corruption-safe reader: a torn prefs file emits empty instead of crashing
// all ~15 collectors (DataStore throws IOException on corrupt reads).
private val Context.safeDataStore: Flow<Preferences>
    get() = dataStore.data.catch { e ->
        android.util.Log.e("UserPrefs", "datastore read failed, using empty", e)
        emit(emptyPreferences())
    }

/**
 * Manages all user settings and anchor coordinates via DataStore.
 */
class UserPreferences(private val context: Context) {

    companion object {
        // Anchor locations
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LON = doublePreferencesKey("home_lon")
        val HOME_RADIUS = doublePreferencesKey("home_radius")
        val STORE_LAT = doublePreferencesKey("store_lat")
        val STORE_LON = doublePreferencesKey("store_lon")
        val STORE_RADIUS = doublePreferencesKey("store_radius")
        val COLLEGE_LAT = doublePreferencesKey("college_lat")
        val COLLEGE_LON = doublePreferencesKey("college_lon")
        val COLLEGE_RADIUS = doublePreferencesKey("college_radius")

        // Vehicle settings
        val VEHICLE_TYPE = stringPreferencesKey("vehicle_type")
        val VEHICLE_NAME = stringPreferencesKey("vehicle_name")
        val VEHICLE_COMPANY = stringPreferencesKey("vehicle_company")
        val VEHICLE_MODEL = stringPreferencesKey("vehicle_model")
        val STARTING_ODOMETER = doublePreferencesKey("starting_odometer")
        val ACCUMULATED_DISTANCE = doublePreferencesKey("accumulated_distance")

        // Fuel settings
        val FUEL_EFFICIENCY_KMPL = doublePreferencesKey("fuel_efficiency_kmpl")
        val FUEL_PRICE_PER_LITRE = doublePreferencesKey("fuel_price_per_litre")
        val DAILY_EMI = doublePreferencesKey("daily_emi")
        val DAILY_PHONE_COST = doublePreferencesKey("daily_phone_cost")

        // Crash detection
        val CRASH_DETECTION_ENABLED = booleanPreferencesKey("crash_detection_enabled")
        val G_FORCE_THRESHOLD = doublePreferencesKey("g_force_threshold")
        val EMERGENCY_CONTACT_1 = stringPreferencesKey("emergency_contact_1")
        val EMERGENCY_CONTACT_2 = stringPreferencesKey("emergency_contact_2")
        val EMERGENCY_CONTACT_3 = stringPreferencesKey("emergency_contact_3")
        val LAST_CRASH_SMS = longPreferencesKey("last_crash_sms")

        // Speed alert
        val SPEED_ALERT_ENABLED = booleanPreferencesKey("speed_alert_enabled")
        val SPEED_LIMIT = doublePreferencesKey("speed_limit")

        // GPS accuracy
        val GPS_MODE = stringPreferencesKey("gps_mode")

        // Onboarding
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")

        // Enabled platforms for gigs
        val ENABLED_PLATFORMS = stringPreferencesKey("enabled_platforms")

        // Theme
        val THEME_MODE = stringPreferencesKey("theme_mode") // system | light | dark
    }

    val homeAnchor: Flow<Triple<Double, Double, Double>?> = context.safeDataStore.map { prefs ->
        val lat = prefs[HOME_LAT]?.takeIf { it.isFinite() }
        val lon = prefs[HOME_LON]?.takeIf { it.isFinite() }
        val radius = prefs[HOME_RADIUS] ?: 150.0
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) Triple(lat, lon, radius) else null
    }

    val storeAnchor: Flow<Triple<Double, Double, Double>?> = context.safeDataStore.map { prefs ->
        val lat = prefs[STORE_LAT]?.takeIf { it.isFinite() }
        val lon = prefs[STORE_LON]?.takeIf { it.isFinite() }
        val radius = prefs[STORE_RADIUS] ?: 100.0
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) Triple(lat, lon, radius) else null
    }

    val collegeAnchor: Flow<Triple<Double, Double, Double>?> = context.safeDataStore.map { prefs ->
        val lat = prefs[COLLEGE_LAT]?.takeIf { it.isFinite() }
        val lon = prefs[COLLEGE_LON]?.takeIf { it.isFinite() }
        val radius = prefs[COLLEGE_RADIUS] ?: 100.0
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) Triple(lat, lon, radius) else null
    }

    val crashDetectionEnabled: Flow<Boolean> = context.safeDataStore.map { prefs ->
        prefs[CRASH_DETECTION_ENABLED] ?: false
    }

    val gForceThreshold: Flow<Double> = context.safeDataStore.map { prefs ->
        (prefs[G_FORCE_THRESHOLD] ?: 4.0).takeIf { it.isFinite() }?.coerceIn(2.5, 8.0) ?: 4.0
    }

    val fuelEfficiency: Flow<Double?> = context.safeDataStore.map { prefs ->
        prefs[FUEL_EFFICIENCY_KMPL]?.takeIf { it.isFinite() && it in 1.0..500.0 }
    }

    val fuelPrice: Flow<Double?> = context.safeDataStore.map { prefs ->
        prefs[FUEL_PRICE_PER_LITRE]?.takeIf { it.isFinite() && it in 0.0..1000.0 }
    }

    val accumulatedDistance: Flow<Double> = context.safeDataStore.map { prefs ->
        (prefs[ACCUMULATED_DISTANCE] ?: 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
    }

    val speedAlertEnabled: Flow<Boolean> = context.safeDataStore.map { prefs ->
        prefs[SPEED_ALERT_ENABLED] ?: false
    }

    val speedLimit: Flow<Double> = context.safeDataStore.map { prefs ->
        (prefs[SPEED_LIMIT] ?: 80.0).takeIf { it.isFinite() }?.coerceIn(20.0, 120.0) ?: 80.0
    }

    val gpsMode: Flow<String> = context.safeDataStore.map { prefs ->
        prefs[GPS_MODE] ?: "balanced"
    }

    val isOnboarded: Flow<Boolean> = context.safeDataStore.map { prefs ->
        prefs[IS_ONBOARDED] ?: false
    }

    val emergencyContacts: Flow<List<String>> = context.safeDataStore.map { prefs ->
        listOfNotNull(
            prefs[EMERGENCY_CONTACT_1],
            prefs[EMERGENCY_CONTACT_2],
            prefs[EMERGENCY_CONTACT_3]
        )
    }

    /** Last successful crash-SMS dispatch (10-min cooldown against stop-and-go spam). */
    suspend fun getLastCrashSms(): Long =
        try { context.safeDataStore.map { it[LAST_CRASH_SMS] ?: 0L }.first() }
        catch (_: Exception) { 0L }

    suspend fun noteCrashSms(now: Long = System.currentTimeMillis()) {
        try { context.dataStore.edit { it[LAST_CRASH_SMS] = now } } catch (_: Exception) {}
    }

    val dailyFixedCosts: Flow<Double> = context.safeDataStore.map { prefs ->
        (prefs[DAILY_EMI] ?: 0.0) + (prefs[DAILY_PHONE_COST] ?: 0.0)
    }

    // Split accessors so Settings can round-trip EMI/phone without wiping either half.
    val dailyEmi: Flow<Double> = context.safeDataStore.map { prefs -> prefs[DAILY_EMI] ?: 0.0 }
    val dailyPhoneCost: Flow<Double> = context.safeDataStore.map { prefs -> prefs[DAILY_PHONE_COST] ?: 0.0 }

    val vehicleCompany: Flow<String> = context.safeDataStore.map { prefs ->
        prefs[VEHICLE_COMPANY] ?: ""
    }

    val vehicleModel: Flow<String> = context.safeDataStore.map { prefs ->
        prefs[VEHICLE_MODEL] ?: ""
    }

    suspend fun setHomeAnchor(lat: Double, lon: Double, radius: Double = 150.0) {
        val safeLat = PrefsValidation.clampLat(lat) ?: return
        val safeLon = PrefsValidation.clampLon(lon) ?: return
        context.dataStore.edit { prefs ->
            prefs[HOME_LAT] = safeLat
            prefs[HOME_LON] = safeLon
            prefs[HOME_RADIUS] = radius.coerceIn(10.0, 5000.0).takeIf { it.isFinite() } ?: 150.0
        }
    }

    suspend fun setStoreAnchor(lat: Double, lon: Double, radius: Double = 100.0) {
        val safeLat = PrefsValidation.clampLat(lat) ?: return
        val safeLon = PrefsValidation.clampLon(lon) ?: return
        context.dataStore.edit { prefs ->
            prefs[STORE_LAT] = safeLat
            prefs[STORE_LON] = safeLon
            prefs[STORE_RADIUS] = radius.coerceIn(10.0, 5000.0).takeIf { it.isFinite() } ?: 100.0
        }
    }

    suspend fun setCollegeAnchor(lat: Double, lon: Double, radius: Double = 100.0) {
        val safeLat = PrefsValidation.clampLat(lat) ?: return
        val safeLon = PrefsValidation.clampLon(lon) ?: return
        context.dataStore.edit { prefs ->
            prefs[COLLEGE_LAT] = safeLat
            prefs[COLLEGE_LON] = safeLon
            prefs[COLLEGE_RADIUS] = radius.coerceIn(10.0, 5000.0).takeIf { it.isFinite() } ?: 100.0
        }
    }

    suspend fun setVehicleInfo(type: String, name: String, company: String, model: String, odometer: Double) {
        context.dataStore.edit { prefs ->
            prefs[VEHICLE_TYPE] = type.take(30)
            prefs[VEHICLE_NAME] = name.take(60)
            prefs[VEHICLE_COMPANY] = company.take(60)
            prefs[VEHICLE_MODEL] = model.take(60)
            prefs[STARTING_ODOMETER] = PrefsValidation.clampNonNegative(odometer)
        }
    }

    suspend fun setFuelSettings(efficiencyKmpl: Double, pricePerLitre: Double) {
        context.dataStore.edit { prefs ->
            prefs[FUEL_EFFICIENCY_KMPL] = efficiencyKmpl.takeIf { it.isFinite() && it in 1.0..500.0 } ?: return@edit
            prefs[FUEL_PRICE_PER_LITRE] = pricePerLitre.takeIf { it.isFinite() && it in 0.0..1000.0 } ?: return@edit
        }
    }

    suspend fun setCrashDetection(enabled: Boolean, threshold: Double = 4.0) {
        // Clamp: 0.01 would fire on every pothole (SMS spam), NaN disables silently.
        val safe = PrefsValidation.clampGForce(threshold)
        context.dataStore.edit { prefs ->
            prefs[CRASH_DETECTION_ENABLED] = enabled
            prefs[G_FORCE_THRESHOLD] = safe
        }
    }

    suspend fun setEmergencyContacts(contacts: List<String>) {
        // Validated + deduped centrally: a planted premium-rate string must not
        // become an SMS destination. Send-time re-validates (root bypass).
        val clean = PrefsValidation.cleanContacts(contacts)
        context.dataStore.edit { prefs ->
            // Clear stale slots first so a shrink from 3→1 doesn't keep ghosts.
            prefs.remove(EMERGENCY_CONTACT_1)
            prefs.remove(EMERGENCY_CONTACT_2)
            prefs.remove(EMERGENCY_CONTACT_3)
            clean.getOrNull(0)?.let { prefs[EMERGENCY_CONTACT_1] = it }
            clean.getOrNull(1)?.let { prefs[EMERGENCY_CONTACT_2] = it }
            clean.getOrNull(2)?.let { prefs[EMERGENCY_CONTACT_3] = it }
        }
    }

    suspend fun addDistance(km: Double) {
        // Per-fix guard: NaN/negative/huge single jumps (mock GPS, corrupt
        // math) must not poison the lifetime odometer.
        val safe = km.takeIf { it.isFinite() && it in 0.0..50.0 } ?: return
        context.dataStore.edit { prefs ->
            val current = (prefs[ACCUMULATED_DISTANCE] ?: 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0
            prefs[ACCUMULATED_DISTANCE] = current + safe
        }
    }

    suspend fun setDailyFixedCosts(emi: Double, phoneCost: Double) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_EMI] = PrefsValidation.clampNonNegative(emi).coerceAtMost(100_000.0)
            prefs[DAILY_PHONE_COST] = PrefsValidation.clampNonNegative(phoneCost).coerceAtMost(100_000.0)
        }
    }

    suspend fun setSpeedAlert(enabled: Boolean, limit: Double = 80.0) {
        // Clamp: 1 km/h would beep constantly, NaN breaks comparisons silently.
        val safe = PrefsValidation.clampSpeedLimit(limit)
        context.dataStore.edit { prefs ->
            prefs[SPEED_ALERT_ENABLED] = enabled
            prefs[SPEED_LIMIT] = safe
        }
    }

    suspend fun setGpsMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[GPS_MODE] = PrefsValidation.clampGpsMode(mode)
        }
    }

    suspend fun setOnboarded(onboarded: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[IS_ONBOARDED] = onboarded
        }
    }

    val enabledPlatforms: Flow<List<String>> = context.safeDataStore.map { prefs ->
        val raw = prefs[ENABLED_PLATFORMS] ?: "Uber,Rapido,Blinkit,Zepto,Swiggy,Zomato,BigBasket"
        // Cap: a planted megabyte CSV must not blow memory or bypass filters.
        raw.take(500).split(",").map { it.trim().take(30) }.filter { it.isNotEmpty() }.take(20)
    }

    suspend fun setEnabledPlatforms(platforms: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[ENABLED_PLATFORMS] = platforms.map { it.trim().take(30) }.filter { it.isNotEmpty() }.take(20).joinToString(",")
        }
    }

    val themeMode: Flow<String> = context.safeDataStore.map { prefs ->
        PrefsValidation.clampTheme(prefs[THEME_MODE] ?: "system")
    }
    suspend fun setThemeMode(mode: String) { context.dataStore.edit { prefs -> prefs[THEME_MODE] = PrefsValidation.clampTheme(mode) } }
}
