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
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class MaintenanceUiState(
    val reminders: List<ServiceReminder> = emptyList(),
    val currentOdometer: Double = 0.0
)

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val serviceReminderDao: ServiceReminderDao,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            combine(
                serviceReminderDao.getAllReminders(),
                prefs.accumulatedDistance
            ) { reminders, odometer ->
                MaintenanceUiState(reminders = reminders, currentOdometer = odometer)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun markDone(reminder: ServiceReminder) {
        viewModelScope.launch {
            val odometer = prefs.accumulatedDistance.first()
            serviceReminderDao.update(
                reminder.copy(lastDoneKm = odometer, lastDoneDate = System.currentTimeMillis(), isSnoozed = false, snoozeUntil = null)
            )
        }
    }

    fun snooze(reminder: ServiceReminder) {
        viewModelScope.launch {
            serviceReminderDao.snoozeReminder(reminder.id, System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L)
        }
    }
}

@Composable
fun MaintenanceScreen(viewModel: MaintenanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(DeepCarbon)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 100.dp)
    ) {
        Text("VEHICLE MAINTENANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text("Service Tracker", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Odometer: ${String.format("%.0f", state.currentOdometer)} km", fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.height(20.dp))

        if (state.reminders.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No vehicle configured.\nSet up in Settings to get maintenance alerts.", color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }

        for (reminder in state.reminders) {
            MaintenanceCard(reminder, state.currentOdometer, { viewModel.markDone(reminder) }, { viewModel.snooze(reminder) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MaintenanceCard(reminder: ServiceReminder, currentOdometer: Double, onMarkDone: () -> Unit, onSnooze: () -> Unit) {
    val kmSince = currentOdometer - reminder.lastDoneKm
    val daysSince = ((System.currentTimeMillis() - reminder.lastDoneDate) / (1000 * 60 * 60 * 24)).toInt()
    val kmProgress = if (reminder.intervalKm < Double.MAX_VALUE / 2) (kmSince / reminder.intervalKm).coerceIn(0.0, 1.0).toFloat() else 0f
    val dayProgress = (daysSince.toFloat() / reminder.intervalDays).coerceIn(0f, 1f)
    val overallProgress = maxOf(kmProgress, dayProgress)

    val statusColor = when { overallProgress >= 0.9f -> CyberCrimson; overallProgress >= 0.7f -> MoltenAmber; else -> EmeraldGreen }
    val icon = when (reminder.reminderType) {
        "oil" -> Icons.Filled.WaterDrop; "air_filter" -> Icons.Filled.Air; "chain" -> Icons.Filled.Link
        "general" -> Icons.Filled.Build; "tyre" -> Icons.Filled.TireRepair; else -> Icons.Filled.Settings
    }
    val title = when (reminder.reminderType) {
        "oil" -> "Engine Oil Change"; "air_filter" -> "Air Filter Check"; "chain" -> "Chain Lubrication"
        "general" -> "General Service"; "tyre" -> "Tyre Pressure Check"; else -> reminder.reminderType
    }

    Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, null, tint = statusColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    if (reminder.isSnoozed) Text("Snoozed", fontSize = 11.sp, color = MoltenAmber)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { overallProgress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = statusColor, trackColor = DividerColor)
            Spacer(Modifier.height(12.dp))
            if (reminder.intervalKm < Double.MAX_VALUE / 2) StatRow("Distance since service", "${kmSince.toInt()} / ${reminder.intervalKm.toInt()} km")
            StatRow("Days since service", "$daysSince / ${reminder.intervalDays} days")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSnooze) { Text("Snooze 3d", color = TextSecondary, fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onMarkDone, colors = ButtonDefaults.filledTonalButtonColors(containerColor = EmeraldGreen.copy(alpha = 0.2f)), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.Check, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark Done", color = EmeraldGreen, fontSize = 13.sp)
                }
            }
        }
    }
}
