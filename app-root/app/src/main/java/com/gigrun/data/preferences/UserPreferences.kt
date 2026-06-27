package com.gigrun.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gigrun_settings")

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

        // Speed alert
        val SPEED_ALERT_ENABLED = booleanPreferencesKey("speed_alert_enabled")
        val SPEED_LIMIT = doublePreferencesKey("speed_limit")

        // GPS accuracy
        val GPS_MODE = stringPreferencesKey("gps_mode")

        // Onboarding
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
    }

    val homeAnchor: Flow<Triple<Double, Double, Double>?> = context.dataStore.data.map { prefs ->
        val lat = prefs[HOME_LAT]
        val lon = prefs[HOME_LON]
        val radius = prefs[HOME_RADIUS] ?: 150.0
        if (lat != null && lon != null) Triple(lat, lon, radius) else null
    }

    val storeAnchor: Flow<Triple<Double, Double, Double>?> = context.dataStore.data.map { prefs ->
        val lat = prefs[STORE_LAT]
        val lon = prefs[STORE_LON]
        val radius = prefs[STORE_RADIUS] ?: 100.0
        if (lat != null && lon != null) Triple(lat, lon, radius) else null
    }

    val collegeAnchor: Flow<Triple<Double, Double, Double>?> = context.dataStore.data.map { prefs ->
        val lat = prefs[COLLEGE_LAT]
        val lon = prefs[COLLEGE_LON]
        val radius = prefs[COLLEGE_RADIUS] ?: 100.0
        if (lat != null && lon != null) Triple(lat, lon, radius) else null
    }

    val crashDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CRASH_DETECTION_ENABLED] ?: false
    }

    val gForceThreshold: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[G_FORCE_THRESHOLD] ?: 4.0
    }

    val fuelEfficiency: Flow<Double?> = context.dataStore.data.map { prefs ->
        prefs[FUEL_EFFICIENCY_KMPL]
    }

    val fuelPrice: Flow<Double?> = context.dataStore.data.map { prefs ->
        prefs[FUEL_PRICE_PER_LITRE]
    }

    val accumulatedDistance: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[ACCUMULATED_DISTANCE] ?: 0.0
    }

    val speedAlertEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SPEED_ALERT_ENABLED] ?: false
    }

    val speedLimit: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[SPEED_LIMIT] ?: 80.0
    }

    val gpsMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GPS_MODE] ?: "balanced"
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_ONBOARDED] ?: false
    }

    val emergencyContacts: Flow<List<String>> = context.dataStore.data.map { prefs ->
        listOfNotNull(
            prefs[EMERGENCY_CONTACT_1],
            prefs[EMERGENCY_CONTACT_2],
            prefs[EMERGENCY_CONTACT_3]
        )
    }

    val dailyFixedCosts: Flow<Double> = context.dataStore.data.map { prefs ->
        (prefs[DAILY_EMI] ?: 0.0) + (prefs[DAILY_PHONE_COST] ?: 0.0)
    }

    val vehicleCompany: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VEHICLE_COMPANY] ?: ""
    }

    val vehicleModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VEHICLE_MODEL] ?: ""
    }

    suspend fun setHomeAnchor(lat: Double, lon: Double, radius: Double = 150.0) {
        context.dataStore.edit { prefs ->
            prefs[HOME_LAT] = lat
            prefs[HOME_LON] = lon
            prefs[HOME_RADIUS] = radius
        }
    }

    suspend fun setStoreAnchor(lat: Double, lon: Double, radius: Double = 100.0) {
        context.dataStore.edit { prefs ->
            prefs[STORE_LAT] = lat
            prefs[STORE_LON] = lon
            prefs[STORE_RADIUS] = radius
        }
    }

    suspend fun setCollegeAnchor(lat: Double, lon: Double, radius: Double = 100.0) {
        context.dataStore.edit { prefs ->
            prefs[COLLEGE_LAT] = lat
            prefs[COLLEGE_LON] = lon
            prefs[COLLEGE_RADIUS] = radius
        }
    }

    suspend fun setVehicleInfo(type: String, name: String, company: String, model: String, odometer: Double) {
        context.dataStore.edit { prefs ->
            prefs[VEHICLE_TYPE] = type
            prefs[VEHICLE_NAME] = name
            prefs[VEHICLE_COMPANY] = company
            prefs[VEHICLE_MODEL] = model
            prefs[STARTING_ODOMETER] = odometer
        }
    }

    suspend fun setFuelSettings(efficiencyKmpl: Double, pricePerLitre: Double) {
        context.dataStore.edit { prefs ->
            prefs[FUEL_EFFICIENCY_KMPL] = efficiencyKmpl
            prefs[FUEL_PRICE_PER_LITRE] = pricePerLitre
        }
    }

    suspend fun setCrashDetection(enabled: Boolean, threshold: Double = 4.0) {
        context.dataStore.edit { prefs ->
            prefs[CRASH_DETECTION_ENABLED] = enabled
            prefs[G_FORCE_THRESHOLD] = threshold
        }
    }

    suspend fun setEmergencyContacts(contacts: List<String>) {
        context.dataStore.edit { prefs ->
            contacts.getOrNull(0)?.let { prefs[EMERGENCY_CONTACT_1] = it }
            contacts.getOrNull(1)?.let { prefs[EMERGENCY_CONTACT_2] = it }
            contacts.getOrNull(2)?.let { prefs[EMERGENCY_CONTACT_3] = it }
        }
    }

    suspend fun addDistance(km: Double) {
        context.dataStore.edit { prefs ->
            val current = prefs[ACCUMULATED_DISTANCE] ?: 0.0
            prefs[ACCUMULATED_DISTANCE] = current + km
        }
    }

    suspend fun setDailyFixedCosts(emi: Double, phoneCost: Double) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_EMI] = emi
            prefs[DAILY_PHONE_COST] = phoneCost
        }
    }

    suspend fun setSpeedAlert(enabled: Boolean, limit: Double = 80.0) {
        context.dataStore.edit { prefs ->
            prefs[SPEED_ALERT_ENABLED] = enabled
            prefs[SPEED_LIMIT] = limit
        }
    }

    suspend fun setGpsMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[GPS_MODE] = mode
        }
    }

    suspend fun setOnboarded(onboarded: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[IS_ONBOARDED] = onboarded
        }
    }
}
