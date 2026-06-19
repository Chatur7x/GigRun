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
            combine(serviceReminderDao.getAllReminders(), prefs.accumulatedDistance) { reminders, odometer ->
                MaintenanceUiState(reminders = reminders, currentOdometer = odometer)
            }.collect { _uiState.value = it }
        }
    }

    fun markDone(reminder: ServiceReminder) {
        viewModelScope.launch {
            val odometer = prefs.accumulatedDistance.first()
            serviceReminderDao.update(reminder.copy(lastDoneKm = odometer, lastDoneDate = System.currentTimeMillis(), isSnoozed = false, snoozeUntil = null))
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
        modifier = Modifier.fillMaxSize().background(SystemBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)
    ) {
        Text(
            "Vehicle",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = LabelPrimary,
            letterSpacing = 0.37.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "Odometer: ${String.format("%.0f", state.currentOdometer)} km",
            fontSize = 15.sp, color = LabelSecondary, letterSpacing = (-0.24).sp
        )
        Spacer(Modifier.height(20.dp))

        if (state.reminders.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = SecondaryBackground, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Build, null, tint = SystemGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No Vehicle Set Up", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LabelPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Configure your vehicle in Settings to get maintenance reminders.", fontSize = 15.sp, color = LabelSecondary, textAlign = TextAlign.Center)
                }
            }
        }

        state.reminders.forEach { reminder ->
            MaintenanceCard(reminder, state.currentOdometer, { viewModel.markDone(reminder) }, { viewModel.snooze(reminder) })
            Spacer(Modifier.height(10.dp))
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

    val statusColor = when {
        overallProgress >= 0.9f -> SystemRed
        overallProgress >= 0.7f -> SystemOrange
        else -> SystemGreen
    }
    val icon = when (reminder.reminderType) {
        "oil" -> Icons.Filled.WaterDrop; "air_filter" -> Icons.Filled.Air; "chain" -> Icons.Filled.Link
        "general" -> Icons.Filled.Build; "tyre" -> Icons.Filled.TireRepair; else -> Icons.Filled.Settings
    }
    val title = when (reminder.reminderType) {
        "oil" -> "Engine Oil"; "air_filter" -> "Air Filter"; "chain" -> "Chain Lube"
        "general" -> "General Service"; "tyre" -> "Tyre Pressure"; else -> reminder.reminderType
    }

    Surface(shape = RoundedCornerShape(14.dp), color = SecondaryBackground, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LabelPrimary, letterSpacing = (-0.41).sp)
                    if (reminder.isSnoozed) Text("Snoozed", fontSize = 13.sp, color = SystemOrange, letterSpacing = (-0.08).sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = statusColor,
                trackColor = SystemGray4,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(12.dp))
            if (reminder.intervalKm < Double.MAX_VALUE / 2) {
                StatRow("Distance", "${kmSince.toInt()} / ${reminder.intervalKm.toInt()} km")
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
            }
            StatRow("Days", "$daysSince / ${reminder.intervalDays} days")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSnooze) {
                    Text("Snooze", color = LabelSecondary, fontSize = 15.sp, letterSpacing = (-0.24).sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onMarkDone,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SystemGreen),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Check, null, tint = LabelPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Done", color = LabelPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.24).sp)
                }
            }
        }
    }
}
