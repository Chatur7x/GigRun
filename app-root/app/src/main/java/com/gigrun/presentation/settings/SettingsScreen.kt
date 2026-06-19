package com.gigrun.presentation.settings

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.data.repository.MaintenanceRepository
import com.gigrun.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: UserPreferences,
    private val maintenanceRepo: MaintenanceRepository
) : ViewModel() {

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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.saveMessage) {
        viewModel.saveMessage?.let { snackbarHostState.showSnackbar(it); viewModel.saveMessage = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = SystemBackground) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)
        ) {
            Text(
                "Settings",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = LabelPrimary,
                letterSpacing = 0.37.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Location Anchors ───────────────────
            SectionHeader("LOCATION ANCHORS")
            SettingsGroup {
                AppleTextField("Home Latitude", viewModel.homeLat) { viewModel.homeLat = it }
                SettingsDivider()
                AppleTextField("Home Longitude", viewModel.homeLon) { viewModel.homeLon = it }
                SettingsDivider()
                AppleTextField("Store/Hub Latitude", viewModel.storeLat) { viewModel.storeLat = it }
                SettingsDivider()
                AppleTextField("Store/Hub Longitude", viewModel.storeLon) { viewModel.storeLon = it }
                SettingsDivider()
                AppleTextField("College Latitude", viewModel.collegeLat) { viewModel.collegeLat = it }
                SettingsDivider()
                AppleTextField("College Longitude", viewModel.collegeLon) { viewModel.collegeLon = it }
            }

            Spacer(Modifier.height(24.dp))

            // ── Vehicle ────────────────────────────
            SectionHeader("VEHICLE")
            SettingsGroup {
                AppleTextField("Vehicle Nickname", viewModel.vehicleName) { viewModel.vehicleName = it }
                SettingsDivider()
                AppleTextField("Company / Make", viewModel.vehicleCompany) { viewModel.vehicleCompany = it }
                SettingsDivider()
                AppleTextField("Model", viewModel.vehicleModel) { viewModel.vehicleModel = it }
                SettingsDivider()
                AppleTextField("Odometer (km)", viewModel.odometer) { viewModel.odometer = it }
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("car", "bike", "auto", "scooter", "motorcycle", "bicycle").forEach { type ->
                        FilterChip(
                            selected = viewModel.vehicleType == type,
                            onClick = { viewModel.vehicleType = type },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, letterSpacing = (-0.24).sp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SystemBlue.copy(alpha = 0.15f),
                                selectedLabelColor = SystemBlue
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Fuel & Costs ───────────────────────
            SectionHeader("FUEL & COSTS")
            SettingsGroup {
                AppleTextField("Fuel Efficiency (km/L)", viewModel.fuelEfficiency) { viewModel.fuelEfficiency = it }
                SettingsDivider()
                AppleTextField("Fuel Price (₹/L)", viewModel.fuelPrice) { viewModel.fuelPrice = it }
                SettingsDivider()
                AppleTextField("Daily EMI (₹)", viewModel.dailyEmi) { viewModel.dailyEmi = it }
                SettingsDivider()
                AppleTextField("Daily Phone Cost (₹)", viewModel.phoneCost) { viewModel.phoneCost = it }
            }

            Spacer(Modifier.height(24.dp))

            // ── Safety ─────────────────────────────
            SectionHeader("SAFETY")
            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Crash Detection", fontSize = 17.sp, color = LabelPrimary, letterSpacing = (-0.41).sp)
                    Switch(
                        checked = viewModel.crashEnabled,
                        onCheckedChange = { viewModel.crashEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LabelPrimary,
                            checkedTrackColor = SystemGreen,
                            uncheckedThumbColor = LabelPrimary,
                            uncheckedTrackColor = SystemGray4
                        )
                    )
                }
                if (viewModel.crashEnabled) {
                    SettingsDivider()
                    AppleTextField("G-Force Threshold", viewModel.gForceThreshold) { viewModel.gForceThreshold = it }
                    SettingsDivider()
                    AppleTextField("Emergency Contact 1", viewModel.contact1) { viewModel.contact1 = it }
                    SettingsDivider()
                    AppleTextField("Emergency Contact 2", viewModel.contact2) { viewModel.contact2 = it }
                    SettingsDivider()
                    AppleTextField("Emergency Contact 3", viewModel.contact3) { viewModel.contact3 = it }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Save Button ────────────────────────
            Button(
                onClick = { viewModel.saveAll() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SystemBlue)
            ) {
                Text("Save All Settings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LabelPrimary, letterSpacing = (-0.41).sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = LabelSecondary,
        letterSpacing = (-0.08).sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SecondaryBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = OpaqueSeparator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun AppleTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = LabelPrimary,
            letterSpacing = (-0.41).sp,
            modifier = Modifier.weight(0.45f)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(0.55f),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 17.sp,
                color = LabelPrimary,
                letterSpacing = (-0.41).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SecondaryBackground,
                unfocusedContainerColor = SecondaryBackground,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = SystemBlue
            ),
            placeholder = {
                Text(
                    label, fontSize = 17.sp, color = LabelTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
