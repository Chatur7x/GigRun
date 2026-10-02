package com.gigrun.presentation.dashboard

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.service.CrashDetectionService
import com.gigrun.service.LocationTrackingService
import com.gigrun.ui.components.*
import com.gigrun.ui.design.LocalGigRunColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {},
    onNavigateToPenalties: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val c = LocalGigRunColors.current
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isShiftExpanded by remember { mutableStateOf(false) }
    // Beast entrance: one-shot stagger cascade (taste-skill §5.C Compose port).
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings", tint = c.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = c.background
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // HERO — full-bleed tinted display block (taste redesign-overhaul:
            // asymmetric left-weighted type over a primary wash; documented
            // exception to the 16dp-card rule — display moments aren't cards).
            // Craft-floor: no eyebrow/kicker; the number carries its own weight.
            val heroAlpha = com.gigrun.ui.design.beastEntranceAlpha(0, entered)
            val heroY = com.gigrun.ui.design.beastEntranceOffsetPx(0, entered)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = heroAlpha; translationY = heroY },
                color = c.primaryContainer,
                contentColor = c.onPrimaryContainer
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    AnimatedRupees(target = state.totalEarned) { text ->
                        Text(
                            text, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                            color = c.onPrimaryContainer, letterSpacing = (-1).sp,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "earned today · ${state.tripsCompleted} trips · ${String.format("%.1f", state.totalDistanceKm)} km",
                        fontSize = 12.sp, color = c.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    if (state.surgeTotal > 0 || state.bonusTotal > 0 || state.tipsTotal > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            if (state.surgeTotal > 0) SurgeBadge(state.surgeTotal)
                            if (state.bonusTotal > 0) BonusBadge(state.bonusTotal)
                            if (state.tipsTotal > 0) TipBadge(state.tipsTotal)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        AssistChip(label = "${state.tripsCompleted} trips", icon = Icons.Default.ReceiptLong)
                        AssistChip(label = "${String.format("%.1f", state.totalDistanceKm)} km", icon = Icons.Default.Route)
                        if (state.isShiftActive) {
                            AssistChip(label = "Active", icon = Icons.Default.FiberManualRecord, tint = c.success)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(horizontal = 16.dp)) {
            // Goal progress — minimal ring + remaining (stagger 1)
            GigCard(modifier = Modifier.fillMaxWidth(), entranceIndex = 1, entranceVisible = entered) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressRing(
                        progress = state.goalProgress,
                        size = 72.dp,
                        strokeWidth = 7.dp,
                        label = "${(state.goalProgress * 100).toInt()}%",
                        sublabel = if (state.isGoalAuto) "auto" else "goal"
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Daily goal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                        Text("₹${state.dailyGoal.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        if (state.goalRemaining > 0) {
                            Text("₹${state.goalRemaining.toInt()} to go · ${state.tripsToGoal} trips", fontSize = 12.sp, color = c.textTertiary)
                        } else {
                            Text("Goal reached — +₹${(-state.goalRemaining).toInt()} over", fontSize = 12.sp, color = c.success)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Net / Gross rate — static support row (ui-ux-pro-max: max 1–2 animated
            // elements per view; hero counter + goal ring are the two moments)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RateCard("Net / hr", "₹${state.netPerHour.toInt()}", c.success, Modifier.weight(1f))
                RateCard("Gross / hr", "₹${state.grossPerHour.toInt()}", c.primary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // Expenses + Net summary — static (supports the two hero moments)
            if (state.expensesTotal > 0) {
                GigCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expenses today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                            Text("₹${state.expensesTotal.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Deductible", fontSize = 12.sp, color = c.textTertiary)
                            Text("₹${state.deductibleTotal.toInt()}", fontSize = 12.sp, color = c.textSecondary)
                        }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net after expenses", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text("₹${(state.totalEarned - state.expensesTotal).toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.success)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Penalties summary — clickable, navigates to penalty tracker
            GigCard(
                onClick = onNavigateToPenalties,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_penalty_card")
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Penalties This Month", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    AnimatedRupees(target = state.penaltiesThisMonth) { text ->
                        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    if (state.disputedThisMonth > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("In Dispute: ₹${state.disputedThisMonth.toInt()}", fontSize = 12.sp, color = c.warning)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Shift summary — collapsible, minimal dividers (static; expand uses spring)
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shift", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                        TextButton(onClick = { isShiftExpanded = !isShiftExpanded }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (isShiftExpanded) "Less" else "More", fontSize = 13.sp, color = c.primary)
                            Icon(if (isShiftExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    MinimalRow("Active time", "${state.shiftTimeMinutes / 60}h ${state.shiftTimeMinutes % 60}m")
                    MinimalRow("Wait time", "${state.waitTimeMinutes} min", valueColor = c.warning)
                    MinimalRow("Distance", "${String.format("%.1f", state.totalDistanceKm)} km")
                    AnimatedVisibility(
                        visible = isShiftExpanded,
                        // motion-framer AnimatePresence port: fade + spring expand, reversed exit.
                        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(250)) +
                                expandVertically(animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f)),
                        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) +
                                shrinkVertically(animationSpec = androidx.compose.animation.core.tween(220))
                    ) {
                        Column {
                            HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 4.dp))
                            MinimalRow("Riding time", "${state.ridingTimeMinutes / 60}h ${state.ridingTimeMinutes % 60}m", valueColor = c.success)
                            MinimalRow("Trips", "${state.tripsCompleted}")
                            MinimalRow("Avg / trip", "₹${state.avgEarningPerTrip.toInt()}")
                            if (state.surgeTotal > 0 || state.tipsTotal > 0) {
                                HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 4.dp))
                                MinimalRow("Surge", "₹${state.surgeTotal.toInt()}", valueColor = c.warning)
                                MinimalRow("Tips", "₹${state.tipsTotal.toInt()}", valueColor = c.success)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tax helper compact — static support card
            state.taxSummary?.let { tax ->
                GigCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tax", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                            Spacer(Modifier.weight(1f))
                            if (tax.gstLiable) {
                                Text("GST liable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.error, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            } else {
                                Text("${(tax.gstProgress * 100).toInt()}% to GST threshold", fontSize = 11.sp, color = c.textTertiary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { tax.gstProgress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = if (tax.gstLiable) c.error else c.primary, trackColor = c.border)
                        Spacer(Modifier.height(8.dp))
                        Text("Projected annual ₹${(tax.annualProjected / 1000).toInt()}k · Save ₹${tax.suggestedMonthlySaving.toInt()}/mo", fontSize = 11.sp, color = c.textTertiary)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Primary CTA — Start/End Shift (haptic confirm on every press)
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // API 33+: starting shift without notification permission hides
                    // the tracking status — nudge once instead of failing silently.
                    if (!state.isShiftActive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        scope.launch { snackbarHostState.showSnackbar("Allow notifications to see live tracking status.") }
                    }
                    if (state.isShiftActive) {
                        context.startForegroundService(Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_STOP })
                        context.stopService(Intent(context, CrashDetectionService::class.java))
                    } else {
                        context.startForegroundService(Intent(context, LocationTrackingService::class.java).apply { action = LocationTrackingService.ACTION_START })
                        scope.launch { if (UserPreferences(context).crashDetectionEnabled.first()) context.startForegroundService(Intent(context, CrashDetectionService::class.java)) }
                    }
                    viewModel.loadTodayStats()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = if (state.isShiftActive) c.error else c.primary)
            ) {
                Icon(if (state.isShiftActive) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.isShiftActive) "End shift" else "Start shift", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { viewModel.generateAndShareReport(context) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export report", fontSize = 13.sp)
            }
            } // close padded content column (hero is full-bleed above it)
        }
    }
}

@Composable
private fun AssistChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalGigRunColors.current
    Surface(shape = MaterialTheme.shapes.extraSmall, color = c.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, c.border)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint ?: c.textSecondary, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
        }
    }
}

@Composable
private fun RateCard(title: String, amount: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val c = LocalGigRunColors.current
    GigCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.textTertiary, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(4.dp))
            Text(amount, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun MinimalRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalGigRunColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor ?: c.textPrimary)
    }
}
