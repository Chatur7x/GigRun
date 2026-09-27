package com.gigrun.presentation.maintenance

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.database.dao.ServiceReminderDao
import com.gigrun.data.database.entities.ServiceReminder
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class MaintenanceUiState(val reminders: List<ServiceReminder> = emptyList(), val currentOdometer: Double = 0.0)

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val serviceReminderDao: ServiceReminderDao,
    private val vehicleDao: com.gigrun.data.database.dao.VehicleDao,
    private val prefs: UserPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(MaintenanceUiState()); val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    init { load() }
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                // Single odometer truth: selected vehicle wins, accumulated distance is fallback.
                combine(
                    serviceReminderDao.getAllReminders(),
                    prefs.accumulatedDistance,
                    vehicleDao.getSelectedVehicle()
                ) { r, o, v -> MaintenanceUiState(r, v?.currentOdometer ?: o) }
                    .catch { e -> android.util.Log.e("MaintVM", "load failed, keeping last", e); emit(_uiState.value) }
                    .collect { _uiState.value = it }
            } catch (e: Exception) {
                android.util.Log.e("MaintVM", "load failed", e)
            }
        }
    }
    fun markDone(r: ServiceReminder) { viewModelScope.launch { serviceReminderDao.update(r.copy(lastDoneKm = _uiState.value.currentOdometer, lastDoneDate = System.currentTimeMillis(), isSnoozed = false, snoozeUntil = null)) } }
    fun snooze(r: ServiceReminder) { viewModelScope.launch { serviceReminderDao.snoozeReminder(r.id, System.currentTimeMillis() + 3 * 86_400_000L) } }
}

@Composable
fun MaintenanceScreen(
    viewModel: MaintenanceViewModel = hiltViewModel(),
    onFuelBikeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val state by viewModel.uiState.collectAsState()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)) {
        Text("Vehicle", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
        Text("Odometer: ${String.format("%.0f", state.currentOdometer)} km", fontSize = 15.sp, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFuelBikeClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.LocalGasStation, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Fuel & Bike")
        }
        Spacer(Modifier.height(12.dp))

        if (state.reminders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Build,
                title = "No Vehicle Set Up",
                subtitle = "Configure your vehicle in Settings.",
                actionLabel = "Open Settings",
                onAction = onSettingsClick,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        state.reminders.forEachIndexed { index, reminder ->
            val kmSince = state.currentOdometer - reminder.lastDoneKm
            val daysSince = ((System.currentTimeMillis() - reminder.lastDoneDate) / 86_400_000).toInt()
            val kmProg = if (reminder.intervalKm < Double.MAX_VALUE / 2) (kmSince / reminder.intervalKm).coerceIn(0.0, 1.0).toFloat() else 0f
            val dayProg = if (reminder.intervalDays > 0) (daysSince.toFloat() / reminder.intervalDays).coerceIn(0f, 1f) else 0f
            val prog = maxOf(kmProg, dayProg)
            val statusColor = when { prog >= 0.9f -> c.error; prog >= 0.7f -> c.warning; else -> c.success }
            val icon = when (reminder.reminderType) { "oil" -> Icons.Filled.WaterDrop; "air_filter" -> Icons.Filled.Air; "chain" -> Icons.Filled.Link; "general" -> Icons.Filled.Build; "tyre" -> Icons.Filled.TireRepair; else -> Icons.Filled.Settings }
            val title = when (reminder.reminderType) { "oil" -> "Engine Oil"; "air_filter" -> "Air Filter"; "chain" -> "Chain Lube"; "general" -> "General Service"; "tyre" -> "Tyre Pressure"; else -> reminder.reminderType }

            Surface(shape = RoundedCornerShape(14.dp), color = c.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                            if (reminder.isSnoozed) Text("Snoozed", fontSize = 13.sp, color = c.warning)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth().height(4.dp), color = statusColor, trackColor = c.surfaceVariant, drawStopIndicator = {})
                    Spacer(Modifier.height(12.dp))
                    if (reminder.intervalKm < Double.MAX_VALUE / 2) { StatRow("Distance", "${kmSince.toInt()} / ${reminder.intervalKm.toInt()} km"); HorizontalDivider(color = c.divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp)) }
                    StatRow("Days", "$daysSince / ${reminder.intervalDays} days")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.snooze(reminder) }) { Text("Snooze", color = c.textSecondary, fontSize = 15.sp) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.markDone(reminder) }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = c.success), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
