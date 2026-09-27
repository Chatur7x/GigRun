package com.gigrun.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    var homeLat by mutableStateOf("")
    var homeLon by mutableStateOf("")
    var storeLat by mutableStateOf("")
    var storeLon by mutableStateOf("")
    var collegeLat by mutableStateOf("")
    var collegeLon by mutableStateOf("")

    var vehicleName by mutableStateOf("")
    var vehicleCompany by mutableStateOf("")
    var vehicleModel by mutableStateOf("")
    var vehicleType by mutableStateOf("motorcycle")
    var odometer by mutableStateOf("")

    var fuelEfficiency by mutableStateOf("")
    var fuelPrice by mutableStateOf("")
    var dailyEmi by mutableStateOf("")
    var phoneCost by mutableStateOf("")

    var crashEnabled by mutableStateOf(false)
    var gForceThreshold by mutableStateOf("4.0")
    var contact1 by mutableStateOf("")
    var contact2 by mutableStateOf("")
    var contact3 by mutableStateOf("")

    var speedAlertEnabled by mutableStateOf(false)
    var speedLimit by mutableStateOf("80")

    var themeMode by mutableStateOf("system")

    var saveMessage by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            prefs.homeAnchor.first()?.let { (lat, lon, _) -> homeLat = lat.toString(); homeLon = lon.toString() }
            prefs.storeAnchor.first()?.let { (lat, lon, _) -> storeLat = lat.toString(); storeLon = lon.toString() }
            prefs.collegeAnchor.first()?.let { (lat, lon, _) -> collegeLat = lat.toString(); collegeLon = lon.toString() }
            vehicleName = prefs.vehicleModel.first() // reuse as name fallback
            vehicleCompany = prefs.vehicleCompany.first()
            vehicleModel = prefs.vehicleModel.first()
            fuelEfficiency = prefs.fuelEfficiency.first()?.toString() ?: ""
            fuelPrice = prefs.fuelPrice.first()?.toString() ?: ""
            // Round-trip split values — never blank-write the other half on save.
            dailyEmi = prefs.dailyEmi.first().takeIf { it > 0 }?.toString() ?: ""
            phoneCost = prefs.dailyPhoneCost.first().takeIf { it > 0 }?.toString() ?: ""
            crashEnabled = prefs.crashDetectionEnabled.first()
            gForceThreshold = prefs.gForceThreshold.first().toString()
            val contacts = prefs.emergencyContacts.first()
            contact1 = contacts.getOrNull(0) ?: ""
            contact2 = contacts.getOrNull(1) ?: ""
            contact3 = contacts.getOrNull(2) ?: ""
            speedAlertEnabled = prefs.speedAlertEnabled.first()
            speedLimit = prefs.speedLimit.first().toInt().toString()
            themeMode = prefs.themeMode.first()
            // odometer from accumulatedDistance as fallback display
            odometer = prefs.accumulatedDistance.first().toInt().toString()
        }
    }

    fun saveAll() {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                // Anchors
                val hLat = homeLat.toDoubleOrNull(); val hLon = homeLon.toDoubleOrNull()
                if (hLat != null && hLon != null) prefs.setHomeAnchor(hLat, hLon)
                val sLat = storeLat.toDoubleOrNull(); val sLon = storeLon.toDoubleOrNull()
                if (sLat != null && sLon != null) prefs.setStoreAnchor(sLat, sLon)
                val cLat = collegeLat.toDoubleOrNull(); val cLon = collegeLon.toDoubleOrNull()
                if (cLat != null && cLon != null) prefs.setCollegeAnchor(cLat, cLon)

                // Vehicle
                val odo = odometer.toDoubleOrNull() ?: 0.0
                prefs.setVehicleInfo(vehicleType, vehicleName, vehicleCompany, vehicleModel, odo)

                // Fuel
                val eff = fuelEfficiency.toDoubleOrNull()
                val price = fuelPrice.toDoubleOrNull()
                if (eff != null && price != null) prefs.setFuelSettings(eff, price)
                val emi = dailyEmi.toDoubleOrNull() ?: 0.0
                val phone = phoneCost.toDoubleOrNull() ?: 0.0
                prefs.setDailyFixedCosts(emi, phone)

                // Crash
                val gForce = gForceThreshold.toDoubleOrNull() ?: 4.0
                prefs.setCrashDetection(crashEnabled, gForce)
                prefs.setEmergencyContacts(listOfNotNull(contact1.takeIf { it.isNotBlank() }, contact2.takeIf { it.isNotBlank() }, contact3.takeIf { it.isNotBlank() }))

                // Speed
                val limit = speedLimit.toDoubleOrNull() ?: 80.0
                prefs.setSpeedAlert(speedAlertEnabled, limit)
                prefs.setThemeMode(themeMode)

                saveMessage = "Settings saved"
            } catch (e: Exception) {
                saveMessage = "Save failed: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }
}
