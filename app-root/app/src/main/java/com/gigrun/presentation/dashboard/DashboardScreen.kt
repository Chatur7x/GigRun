package com.gigrun.presentation.dashboard

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.service.CrashDetectionService
import com.gigrun.service.LocationTrackingService
import com.gigrun.ui.components.*
import com.gigrun.ui.theme.Apple
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val c = Apple.colors
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFuelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadTodayStats() }

    Column(
        modifier = Modifier.fillMaxSize().background(c.groupedBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 100.dp)
    ) {
        // ── Header ─────────────────────────────────
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Dashboard", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label)
            FilledIconButton(
                onClick = {
                    if (state.isShiftActive) {
                        context.startForegroundService(Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_STOP })
                        context.stopService(Intent(context, CrashDetectionService::class.java))
                    } else {
                        context.startForegroundService(Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_START })
                        scope.launch { if (UserPreferences(context).crashDetectionEnabled.first()) context.startForegroundService(Intent(context, CrashDetectionService::class.java)) }
                    }
                    viewModel.loadTodayStats()
                },
                modifier = Modifier.size(44.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (state.isShiftActive) c.red else c.green)
            ) {
                Icon(if (state.isShiftActive) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = c.label, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Main Earnings ──────────────────────────
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
            Column(Modifier.padding(20.dp)) {
                Text("Today's Earnings", fontSize = 13.sp, color = c.secondaryLabel)
                Spacer(Modifier.height(4.dp))
                Text("₹ ${state.totalEarned.toInt()}", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = c.label)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoPill("${state.tripsCompleted} trips", c.blue)
                    InfoPill("${String.format("%.1f", state.totalDistanceKm)} km", c.teal)
                    if (state.isShiftActive) InfoPill("Live", c.green)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningsCard("Net ₹/hr", "₹${state.netPerHour.toInt()}", accentColor = c.green, modifier = Modifier.weight(1f))
            EarningsCard("Gross ₹/hr", "₹${state.grossPerHour.toInt()}", accentColor = c.blue, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // ── Shift Breakdown ────────────────────────
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
            Column(Modifier.padding(20.dp)) {
                Text("Shift Breakdown", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label)
                Spacer(Modifier.height(14.dp))
                StatRow("Active time", "${state.shiftTimeMinutes / 60}h ${state.shiftTimeMinutes % 60}m")
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Wait time", "${state.waitTimeMinutes} min", valueColor = c.orange)
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Riding time", "${state.ridingTimeMinutes / 60}h ${state.ridingTimeMinutes % 60}m", valueColor = c.green)
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Trips", "${state.tripsCompleted}")
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Avg/trip", "₹${state.avgEarningPerTrip.toInt()}")
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Distance", "${String.format("%.1f", state.totalDistanceKm)} km")
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Riding Score ───────────────────────────
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Riding Score", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label)
                    Text("${state.ridingScore}", fontSize = 28.sp, fontWeight = FontWeight.Black,
                        color = when {
                            state.ridingScore >= 80 -> c.green
                            state.ridingScore >= 60 -> c.orange
                            else -> c.red
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(state.ridingTip, fontSize = 12.sp, color = c.secondaryLabel)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.ridingScore < 90) {
                        InfoPill("Harsh braking", c.red)
                    }
                    if (state.ridingScore < 100) {
                        Text("Speed limit: ${state.speedAlertLimit.toInt()} km/h", fontSize = 11.sp, color = c.gray)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Break-Even ─────────────────────────────
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Break-Even", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                BreakEvenMeter(earned = state.netEarned, breakEvenTarget = state.breakEvenTarget)
                Spacer(Modifier.height(16.dp))
                StatRow("Fuel cost", "₹${state.fuelCost.toInt()}", valueColor = c.orange)
                HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Net earnings", "₹${state.netEarned.toInt()}", valueColor = if (state.netEarned >= 0) c.green else c.red)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { showFuelDialog = true }, Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = c.tertiaryFill)) {
                    Icon(Icons.Filled.LocalGasStation, null, tint = c.orange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enter Fuel Cost", color = c.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(onClick = { viewModel.generateAndShareReport(context) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = c.blue)) {
            Icon(Icons.Filled.PictureAsPdf, null, tint = c.label, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Shift Report", color = c.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showFuelDialog) {
        var fuelInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFuelDialog = false },
            title = { Text("Fuel Cost", color = c.label, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(fuelInput, { fuelInput = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Amount in ₹") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, cursorColor = c.blue, focusedLabelColor = c.blue))
            },
            confirmButton = { TextButton(onClick = { fuelInput.toDoubleOrNull()?.let { viewModel.setFuelCost(it) }; showFuelDialog = false }) { Text("Save", color = c.blue, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showFuelDialog = false }) { Text("Cancel", color = c.red) } },
            containerColor = c.tertiaryBackground
        )
    }
}

@Composable
private fun InfoPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
}
