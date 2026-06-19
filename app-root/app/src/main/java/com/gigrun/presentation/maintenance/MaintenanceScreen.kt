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
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.theme.Apple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class MaintenanceUiState(val reminders: List<ServiceReminder> = emptyList(), val currentOdometer: Double = 0.0)

@HiltViewModel
class MaintenanceViewModel @Inject constructor(private val serviceReminderDao: ServiceReminderDao, private val prefs: UserPreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(MaintenanceUiState()); val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    init { load() }
    fun load() { loadJob?.cancel(); loadJob = viewModelScope.launch { combine(serviceReminderDao.getAllReminders(), prefs.accumulatedDistance) { r, o -> MaintenanceUiState(r, o) }.collect { _uiState.value = it } } }
    fun markDone(r: ServiceReminder) { viewModelScope.launch { serviceReminderDao.update(r.copy(lastDoneKm = prefs.accumulatedDistance.first(), lastDoneDate = System.currentTimeMillis(), isSnoozed = false, snoozeUntil = null)) } }
    fun snooze(r: ServiceReminder) { viewModelScope.launch { serviceReminderDao.snoozeReminder(r.id, System.currentTimeMillis() + 3 * 86_400_000L) } }
}

@Composable
fun MaintenanceScreen(viewModel: MaintenanceViewModel = hiltViewModel()) {
    val c = Apple.colors
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(c.groupedBackground).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)) {
        Text("Vehicle", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.padding(vertical = 8.dp))
        Text("Odometer: ${String.format("%.0f", state.currentOdometer)} km", fontSize = 15.sp, color = c.secondaryLabel)
        Spacer(Modifier.height(20.dp))

        if (state.reminders.isEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Build, null, tint = c.gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No Vehicle Set Up", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.label, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Configure your vehicle in Settings.", fontSize = 15.sp, color = c.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
        }

        state.reminders.forEach { reminder ->
            val kmSince = state.currentOdometer - reminder.lastDoneKm
            val daysSince = ((System.currentTimeMillis() - reminder.lastDoneDate) / 86_400_000).toInt()
            val kmProg = if (reminder.intervalKm < Double.MAX_VALUE / 2) (kmSince / reminder.intervalKm).coerceIn(0.0, 1.0).toFloat() else 0f
            val dayProg = (daysSince.toFloat() / reminder.intervalDays).coerceIn(0f, 1f)
            val prog = maxOf(kmProg, dayProg)
            val statusColor = when { prog >= 0.9f -> c.red; prog >= 0.7f -> c.orange; else -> c.green }
            val icon = when (reminder.reminderType) { "oil" -> Icons.Filled.WaterDrop; "air_filter" -> Icons.Filled.Air; "chain" -> Icons.Filled.Link; "general" -> Icons.Filled.Build; "tyre" -> Icons.Filled.TireRepair; else -> Icons.Filled.Settings }
            val title = when (reminder.reminderType) { "oil" -> "Engine Oil"; "air_filter" -> "Air Filter"; "chain" -> "Chain Lube"; "general" -> "General Service"; "tyre" -> "Tyre Pressure"; else -> reminder.reminderType }

            Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                            if (reminder.isSnoozed) Text("Snoozed", fontSize = 13.sp, color = c.orange)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth().height(4.dp), color = statusColor, trackColor = c.gray4, drawStopIndicator = {})
                    Spacer(Modifier.height(12.dp))
                    if (reminder.intervalKm < Double.MAX_VALUE / 2) { StatRow("Distance", "${kmSince.toInt()} / ${reminder.intervalKm.toInt()} km"); HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp)) }
                    StatRow("Days", "$daysSince / ${reminder.intervalDays} days")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.snooze(reminder) }) { Text("Snooze", color = c.secondaryLabel, fontSize = 15.sp) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.markDone(reminder) }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = c.green), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
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
