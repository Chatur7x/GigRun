package com.gigrun.presentation.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.data.repository.MaintenanceRepository
import com.gigrun.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = UserPreferences(application)
    private val database = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "gigrun_db")
        .fallbackToDestructiveMigration().build()
    private val maintenanceRepo = MaintenanceRepository(database.serviceReminderDao())

    var homeLat by mutableStateOf("")
    var homeLon by mutableStateOf("")
    var storeLat by mutableStateOf("")
    var storeLon by mutableStateOf("")
    var collegeLat by mutableStateOf("")
    var collegeLon by mutableStateOf("")
    var vehicleType by mutableStateOf("scooter")
    var vehicleName by mutableStateOf("")
    var vehicleCompany by mutableStateOf("")
    var vehicleModel by mutableStateOf("")
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
    var saveMessage by mutableStateOf<String?>(null)

    init { loadCurrentSettings() }

    private fun loadCurrentSettings() {
        viewModelScope.launch {
            prefs.homeAnchor.first()?.let { (lat, lon, _) -> homeLat = lat.toString(); homeLon = lon.toString() }
            prefs.storeAnchor.first()?.let { (lat, lon, _) -> storeLat = lat.toString(); storeLon = lon.toString() }
            prefs.collegeAnchor.first()?.let { (lat, lon, _) -> collegeLat = lat.toString(); collegeLon = lon.toString() }
            prefs.fuelEfficiency.first()?.let { fuelEfficiency = it.toString() }
            prefs.fuelPrice.first()?.let { fuelPrice = it.toString() }
            prefs.gForceThreshold.first().let { gForceThreshold = it.toString() }
            prefs.crashDetectionEnabled.first().let { crashEnabled = it }
            prefs.vehicleCompany.first().let { vehicleCompany = it }
            prefs.vehicleModel.first().let { vehicleModel = it }
            prefs.emergencyContacts.first().let { contacts ->
                contact1 = contacts.getOrElse(0) { "" }
                contact2 = contacts.getOrElse(1) { "" }
                contact3 = contacts.getOrElse(2) { "" }
            }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            try {
                val hLat = homeLat.toDoubleOrNull(); val hLon = homeLon.toDoubleOrNull()
                if (hLat != null && hLon != null) prefs.setHomeAnchor(hLat, hLon)
                val sLat = storeLat.toDoubleOrNull(); val sLon = storeLon.toDoubleOrNull()
                if (sLat != null && sLon != null) prefs.setStoreAnchor(sLat, sLon)
                val cLat = collegeLat.toDoubleOrNull(); val cLon = collegeLon.toDoubleOrNull()
                if (cLat != null && cLon != null) prefs.setCollegeAnchor(cLat, cLon)
                if (vehicleName.isNotBlank()) {
                    val odo = odometer.toDoubleOrNull() ?: 0.0
                    prefs.setVehicleInfo(vehicleType, vehicleName, vehicleCompany, vehicleModel, odo)
                    maintenanceRepo.initializeDefaults(vehicleName, vehicleType)
                }
                val eff = fuelEfficiency.toDoubleOrNull(); val price = fuelPrice.toDoubleOrNull()
                if (eff != null && price != null) prefs.setFuelSettings(eff, price)
                prefs.setDailyFixedCosts(dailyEmi.toDoubleOrNull() ?: 0.0, phoneCost.toDoubleOrNull() ?: 0.0)
                prefs.setCrashDetection(crashEnabled, gForceThreshold.toDoubleOrNull() ?: 4.0)
                prefs.setEmergencyContacts(listOfNotNull(contact1.ifBlank { null }, contact2.ifBlank { null }, contact3.ifBlank { null }))
                prefs.setOnboarded(true)
                saveMessage = "Settings saved successfully!"
            } catch (e: Exception) { saveMessage = "Error: ${e.message}" }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.saveMessage) { viewModel.saveMessage?.let { snackbarHostState.showSnackbar(it); viewModel.saveMessage = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = DeepCarbon) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 100.dp)
        ) {
            Text("SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text("Configuration", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            SettingsSection("Location Anchors") {
                SettingsTextField("Home Latitude", viewModel.homeLat) { viewModel.homeLat = it }
                SettingsTextField("Home Longitude", viewModel.homeLon) { viewModel.homeLon = it }
                Spacer(Modifier.height(8.dp))
                SettingsTextField("Store/Hub Latitude", viewModel.storeLat) { viewModel.storeLat = it }
                SettingsTextField("Store/Hub Longitude", viewModel.storeLon) { viewModel.storeLon = it }
                Spacer(Modifier.height(8.dp))
                SettingsTextField("College Latitude", viewModel.collegeLat) { viewModel.collegeLat = it }
                SettingsTextField("College Longitude", viewModel.collegeLon) { viewModel.collegeLon = it }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("Vehicle") {
                SettingsTextField("Vehicle Nickname", viewModel.vehicleName) { viewModel.vehicleName = it }
                SettingsTextField("Company / Make", viewModel.vehicleCompany) { viewModel.vehicleCompany = it }
                SettingsTextField("Model", viewModel.vehicleModel) { viewModel.vehicleModel = it }
                Spacer(Modifier.height(8.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("car", "bike", "auto", "scooter", "motorcycle", "bicycle").forEach { type ->
                        FilterChip(
                            selected = viewModel.vehicleType == type, onClick = { viewModel.vehicleType = type },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberCyan.copy(alpha = 0.2f), selectedLabelColor = CyberCyan)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingsTextField("Current Odometer (km)", viewModel.odometer) { viewModel.odometer = it }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("Fuel & Costs") {
                SettingsTextField("Fuel Efficiency (km/L)", viewModel.fuelEfficiency) { viewModel.fuelEfficiency = it }
                SettingsTextField("Fuel Price (₹/L)", viewModel.fuelPrice) { viewModel.fuelPrice = it }
                SettingsTextField("Daily EMI (₹)", viewModel.dailyEmi) { viewModel.dailyEmi = it }
                SettingsTextField("Daily Phone Cost (₹)", viewModel.phoneCost) { viewModel.phoneCost = it }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("Safety & Crash Detection") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Crash Detection", color = TextPrimary)
                    Switch(checked = viewModel.crashEnabled, onCheckedChange = { viewModel.crashEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = CyberCyan, checkedThumbColor = DeepCarbon))
                }
                if (viewModel.crashEnabled) {
                    SettingsTextField("G-Force Threshold", viewModel.gForceThreshold) { viewModel.gForceThreshold = it }
                    SettingsTextField("Emergency Contact 1", viewModel.contact1) { viewModel.contact1 = it }
                    SettingsTextField("Emergency Contact 2", viewModel.contact2) { viewModel.contact2 = it }
                    SettingsTextField("Emergency Contact 3", viewModel.contact3) { viewModel.contact3 = it }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveAll() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Icon(Icons.Filled.Save, null, tint = DeepCarbon)
                Spacer(Modifier.width(8.dp))
                Text("Save All Settings", color = DeepCarbon, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor,
            focusedLabelColor = CyberCyan, cursorColor = CyberCyan
        )
    )
}
