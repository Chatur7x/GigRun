package com.gigrun.presentation.dashboard

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.service.CrashDetectionService
import com.gigrun.service.LocationTrackingService
import com.gigrun.ui.components.*
import com.gigrun.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFuelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadTodayStats() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCarbon)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 100.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TODAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 2.sp)
                Text("Dashboard", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            FilledTonalButton(
                onClick = {
                    if (state.isShiftActive) {
                        // Stop shift
                        val stopIntent = Intent(context, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_STOP
                        }
                        context.startForegroundService(stopIntent)
                        // Stop crash detection
                        context.stopService(Intent(context, CrashDetectionService::class.java))
                    } else {
                        // Start shift
                        val startIntent = Intent(context, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_START
                        }
                        context.startForegroundService(startIntent)
                        // Start crash detection if enabled
                        scope.launch {
                            val prefs = UserPreferences(context)
                            val crashEnabled = prefs.crashDetectionEnabled.first()
                            if (crashEnabled) {
                                val crashIntent = Intent(context, CrashDetectionService::class.java)
                                context.startForegroundService(crashIntent)
                            }
                        }
                    }
                    viewModel.loadTodayStats()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (state.isShiftActive) CyberCrimson.copy(alpha = 0.2f) else EmeraldGreen.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (state.isShiftActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (state.isShiftActive) CyberCrimson else EmeraldGreen
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isShiftActive) "End Shift" else "Start Shift",
                    color = if (state.isShiftActive) CyberCrimson else EmeraldGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        EarningsCard(
            title = "TOTAL EARNED",
            amount = "₹ ${state.totalEarned.toInt()}",
            subtitle = "${state.tripsCompleted} trips · ${String.format("%.1f", state.totalDistanceKm)} km"
        )

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningsCard("NET ₹/HR", "₹${state.netPerHour.toInt()}", accentColor = EmeraldGreen, modifier = Modifier.weight(1f))
            EarningsCard("GROSS ₹/HR", "₹${state.grossPerHour.toInt()}", accentColor = MoltenAmber, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Shift breakdown
        Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("SHIFT BREAKDOWN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(12.dp))
                StatRow("Active shift time", "${state.shiftTimeMinutes / 60}h ${state.shiftTimeMinutes % 60}m")
                StatRow("Unpaid wait time", "${state.waitTimeMinutes} min", valueColor = CyberCrimson)
                StatRow("Actual riding time", "${state.ridingTimeMinutes / 60}h ${state.ridingTimeMinutes % 60}m", valueColor = EmeraldGreen)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DividerColor)
                StatRow("Trips completed", "${state.tripsCompleted}")
                StatRow("Avg earning/trip", "₹${state.avgEarningPerTrip.toInt()}")
                StatRow("Total distance", "${String.format("%.1f", state.totalDistanceKm)} km")
            }
        }

        Spacer(Modifier.height(20.dp))

        // Break-even section
        Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BREAK-EVEN TRACKER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.2.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                BreakEvenMeter(earned = state.netEarned, breakEvenTarget = state.breakEvenTarget)
                Spacer(Modifier.height(16.dp))
                StatRow("Fuel cost", "₹${state.fuelCost.toInt()}", valueColor = MoltenAmber)
                StatRow("Net earnings", "₹${state.netEarned.toInt()}", valueColor = if (state.netEarned >= 0) EmeraldGreen else CyberCrimson)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showFuelDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MoltenAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enter Fuel Cost")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // PDF Export Button
        Button(
            onClick = { viewModel.generateAndShareReport(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
        ) {
            Icon(Icons.Filled.PictureAsPdf, null, tint = DeepCarbon)
            Spacer(Modifier.width(8.dp))
            Text("Export Shift Report", color = DeepCarbon, fontWeight = FontWeight.Bold)
        }
    }

    if (showFuelDialog) {
        var fuelInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFuelDialog = false },
            title = { Text("Enter Fuel Cost", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = fuelInput,
                    onValueChange = { fuelInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount in ₹") },
                    singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = { fuelInput.toDoubleOrNull()?.let { viewModel.setFuelCost(it) }; showFuelDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showFuelDialog = false }) { Text("Cancel") } },
            containerColor = CardSurface
        )
    }
}
