package com.gigrun.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.data.repository.MaintenanceRepository
import com.gigrun.ui.theme.Apple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: UserPreferences, private val maintenanceRepo: MaintenanceRepository
) : ViewModel() {
    var homeLat by mutableStateOf(""); var homeLon by mutableStateOf("")
    var storeLat by mutableStateOf(""); var storeLon by mutableStateOf("")
    var collegeLat by mutableStateOf(""); var collegeLon by mutableStateOf("")
    var vehicleType by mutableStateOf("scooter"); var vehicleName by mutableStateOf("")
    var vehicleCompany by mutableStateOf(""); var vehicleModel by mutableStateOf("")
    var odometer by mutableStateOf(""); var fuelEfficiency by mutableStateOf("")
    var fuelPrice by mutableStateOf(""); var dailyEmi by mutableStateOf("")
    var phoneCost by mutableStateOf(""); var crashEnabled by mutableStateOf(false)
    var gForceThreshold by mutableStateOf("4.0")
    var contact1 by mutableStateOf(""); var contact2 by mutableStateOf(""); var contact3 by mutableStateOf("")
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
            prefs.emergencyContacts.first().let { contact1 = it.getOrElse(0) { "" }; contact2 = it.getOrElse(1) { "" }; contact3 = it.getOrElse(2) { "" } }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            try {
                homeLat.toDoubleOrNull()?.let { lat -> homeLon.toDoubleOrNull()?.let { lon -> prefs.setHomeAnchor(lat, lon) } }
                storeLat.toDoubleOrNull()?.let { lat -> storeLon.toDoubleOrNull()?.let { lon -> prefs.setStoreAnchor(lat, lon) } }
                collegeLat.toDoubleOrNull()?.let { lat -> collegeLon.toDoubleOrNull()?.let { lon -> prefs.setCollegeAnchor(lat, lon) } }
                if (vehicleName.isNotBlank()) { prefs.setVehicleInfo(vehicleType, vehicleName, vehicleCompany, vehicleModel, odometer.toDoubleOrNull() ?: 0.0); maintenanceRepo.initializeDefaults(vehicleName, vehicleType) }
                fuelEfficiency.toDoubleOrNull()?.let { eff -> fuelPrice.toDoubleOrNull()?.let { price -> prefs.setFuelSettings(eff, price) } }
                prefs.setDailyFixedCosts(dailyEmi.toDoubleOrNull() ?: 0.0, phoneCost.toDoubleOrNull() ?: 0.0)
                prefs.setCrashDetection(crashEnabled, gForceThreshold.toDoubleOrNull() ?: 4.0)
                prefs.setEmergencyContacts(listOfNotNull(contact1.ifBlank { null }, contact2.ifBlank { null }, contact3.ifBlank { null }))
                prefs.setOnboarded(true); saveMessage = "Settings saved successfully!"
            } catch (e: Exception) { saveMessage = "Error: ${e.message}" }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val c = Apple.colors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.saveMessage) { viewModel.saveMessage?.let { snackbarHostState.showSnackbar(it); viewModel.saveMessage = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = c.groupedBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)) {
            Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(12.dp))

            SectionHeader("LOCATION ANCHORS", c.secondaryLabel)
            AppleGroup(c.secondaryGroupedBackground) {
                AppleRow("Home Lat", viewModel.homeLat, c) { viewModel.homeLat = it }; InsetDivider(c)
                AppleRow("Home Lon", viewModel.homeLon, c) { viewModel.homeLon = it }; InsetDivider(c)
                AppleRow("Store Lat", viewModel.storeLat, c) { viewModel.storeLat = it }; InsetDivider(c)
                AppleRow("Store Lon", viewModel.storeLon, c) { viewModel.storeLon = it }; InsetDivider(c)
                AppleRow("College Lat", viewModel.collegeLat, c) { viewModel.collegeLat = it }; InsetDivider(c)
                AppleRow("College Lon", viewModel.collegeLon, c) { viewModel.collegeLon = it }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("VEHICLE", c.secondaryLabel)
            AppleGroup(c.secondaryGroupedBackground) {
                AppleRow("Nickname", viewModel.vehicleName, c) { viewModel.vehicleName = it }; InsetDivider(c)
                AppleRow("Company", viewModel.vehicleCompany, c) { viewModel.vehicleCompany = it }; InsetDivider(c)
                AppleRow("Model", viewModel.vehicleModel, c) { viewModel.vehicleModel = it }; InsetDivider(c)
                AppleRow("Odometer", viewModel.odometer, c) { viewModel.odometer = it }
            }
            Spacer(Modifier.height(8.dp))
            AppleGroup(c.secondaryGroupedBackground) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("car", "bike", "auto", "scooter", "motorcycle", "bicycle").forEach { type ->
                        FilterChip(selected = viewModel.vehicleType == type, onClick = { viewModel.vehicleType = type },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 15.sp) }, shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.blue.copy(alpha = 0.15f), selectedLabelColor = c.blue))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("FUEL & COSTS", c.secondaryLabel)
            AppleGroup(c.secondaryGroupedBackground) {
                AppleRow("Efficiency (km/L)", viewModel.fuelEfficiency, c) { viewModel.fuelEfficiency = it }; InsetDivider(c)
                AppleRow("Price (₹/L)", viewModel.fuelPrice, c) { viewModel.fuelPrice = it }; InsetDivider(c)
                AppleRow("Daily EMI (₹)", viewModel.dailyEmi, c) { viewModel.dailyEmi = it }; InsetDivider(c)
                AppleRow("Phone Cost (₹)", viewModel.phoneCost, c) { viewModel.phoneCost = it }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("SAFETY", c.secondaryLabel)
            AppleGroup(c.secondaryGroupedBackground) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Crash Detection", fontSize = 17.sp, color = c.label)
                    Switch(viewModel.crashEnabled, { viewModel.crashEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.green, uncheckedThumbColor = Color.White, uncheckedTrackColor = c.fill))
                }
                if (viewModel.crashEnabled) {
                    InsetDivider(c)
                    AppleRow("G-Force", viewModel.gForceThreshold, c) { viewModel.gForceThreshold = it }; InsetDivider(c)
                    AppleRow("Contact 1", viewModel.contact1, c) { viewModel.contact1 = it }; InsetDivider(c)
                    AppleRow("Contact 2", viewModel.contact2, c) { viewModel.contact2 = it }; InsetDivider(c)
                    AppleRow("Contact 3", viewModel.contact3, c) { viewModel.contact3 = it }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(onClick = { viewModel.saveAll() }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = c.blue)) {
                Text("Save All Settings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable private fun SectionHeader(title: String, color: Color) { Text(title, fontSize = 13.sp, color = color, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)) }
@Composable private fun AppleGroup(bgColor: Color, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(14.dp), color = bgColor, modifier = Modifier.fillMaxWidth()) { Column(content = content) } }
@Composable private fun InsetDivider(c: com.gigrun.ui.theme.AppleColors) { HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp)) }
@Composable
private fun AppleRow(label: String, value: String, c: com.gigrun.ui.theme.AppleColors, onValueChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 17.sp, color = c.label, modifier = Modifier.weight(0.45f))
        TextField(value, onValueChange, singleLine = true, modifier = Modifier.weight(0.55f),
            textStyle = TextStyle(fontSize = 17.sp, color = c.label, textAlign = TextAlign.End),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = c.blue),
            placeholder = { Text(label, fontSize = 17.sp, color = c.tertiaryLabel, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) })
    }
}
