package com.gigrun.presentation.dashboard

import android.content.Intent
import androidx.compose.animation.core.*
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
            .background(SystemBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 100.dp)
    ) {
        // ── Header ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dashboard",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = LabelPrimary,
                letterSpacing = 0.37.sp
            )
            // Shift toggle button
            FilledIconButton(
                onClick = {
                    if (state.isShiftActive) {
                        context.startForegroundService(
                            Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_STOP }
                        )
                        context.stopService(Intent(context, CrashDetectionService::class.java))
                    } else {
                        context.startForegroundService(
                            Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_START }
                        )
                        scope.launch {
                            if (UserPreferences(context).crashDetectionEnabled.first()) {
                                context.startForegroundService(Intent(context, CrashDetectionService::class.java))
                            }
                        }
                    }
                    viewModel.loadTodayStats()
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (state.isShiftActive) SystemRed else SystemGreen
                )
            ) {
                Icon(
                    if (state.isShiftActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isShiftActive) "End Shift" else "Start Shift",
                    tint = LabelPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Main Earnings Card ─────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SecondaryBackground
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Today's Earnings", fontSize = 13.sp, color = LabelSecondary, letterSpacing = (-0.08).sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "₹ ${state.totalEarned.toInt()}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = LabelPrimary,
                    letterSpacing = 0.37.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoPill("${state.tripsCompleted} trips", SystemBlue)
                    InfoPill("${String.format("%.1f", state.totalDistanceKm)} km", SystemTeal)
                    if (state.isShiftActive) InfoPill("Live", SystemGreen)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Per-hour cards ─────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningsCard("Net ₹/hr", "₹${state.netPerHour.toInt()}", accentColor = SystemGreen, modifier = Modifier.weight(1f))
            EarningsCard("Gross ₹/hr", "₹${state.grossPerHour.toInt()}", accentColor = SystemBlue, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // ── Shift Breakdown ────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SecondaryBackground
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Shift Breakdown", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LabelPrimary, letterSpacing = 0.38.sp)
                Spacer(Modifier.height(14.dp))
                StatRow("Active time", "${state.shiftTimeMinutes / 60}h ${state.shiftTimeMinutes % 60}m")
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Wait time", "${state.waitTimeMinutes} min", valueColor = SystemOrange)
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Riding time", "${state.ridingTimeMinutes / 60}h ${state.ridingTimeMinutes % 60}m", valueColor = SystemGreen)
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Trips", "${state.tripsCompleted}")
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Avg/trip", "₹${state.avgEarningPerTrip.toInt()}")
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Distance", "${String.format("%.1f", state.totalDistanceKm)} km")
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Break-Even ─────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = SecondaryBackground
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Break-Even", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LabelPrimary, letterSpacing = 0.38.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                BreakEvenMeter(earned = state.netEarned, breakEvenTarget = state.breakEvenTarget)
                Spacer(Modifier.height(16.dp))
                StatRow("Fuel cost", "₹${state.fuelCost.toInt()}", valueColor = SystemOrange)
                HorizontalDivider(color = OpaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                StatRow("Net earnings", "₹${state.netEarned.toInt()}", valueColor = if (state.netEarned >= 0) SystemGreen else SystemRed)
                Spacer(Modifier.height(14.dp))
                // Fuel cost button
                Button(
                    onClick = { showFuelDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SystemGray4)
                ) {
                    Icon(Icons.Filled.LocalGasStation, null, tint = SystemOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enter Fuel Cost", color = LabelPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.24).sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Export Button ───────────────────────────
        Button(
            onClick = { viewModel.generateAndShareReport(context) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SystemBlue)
        ) {
            Icon(Icons.Filled.PictureAsPdf, null, tint = LabelPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Shift Report", color = LabelPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.41).sp)
        }
    }

    // ── Fuel Dialog ────────────────────────────────
    if (showFuelDialog) {
        var fuelInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFuelDialog = false },
            title = { Text("Fuel Cost", color = LabelPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = fuelInput,
                    onValueChange = { fuelInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount in ₹") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SystemBlue,
                        cursorColor = SystemBlue,
                        focusedLabelColor = SystemBlue
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { fuelInput.toDoubleOrNull()?.let { viewModel.setFuelCost(it) }; showFuelDialog = false }) {
                    Text("Save", color = SystemBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFuelDialog = false }) {
                    Text("Cancel", color = SystemRed)
                }
            },
            containerColor = TertiaryBackground
        )
    }
}

@Composable
private fun InfoPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        letterSpacing = 0.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
