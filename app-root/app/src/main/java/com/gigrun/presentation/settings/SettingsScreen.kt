package com.gigrun.presentation.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gigrun.core.utils.BatteryOptimizationHelper
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.SectionHeader
import com.gigrun.ui.design.LocalGigRunColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateExpenses: () -> Unit = {},
    onNavigateGoals: () -> Unit = {},
    onNavigateTax: () -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val c = LocalGigRunColors.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Contextual SMS grant: asked when crash detection is enabled, not bundled
    // into the cold-start location batch (Play policy + grant-rate friendly).
    val smsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Crash SMS disabled — alerts can't be sent.") }
        }
    }
    // R&D fix: LaunchedEffect on plain var never retriggers — key on the message value.
    val saveMsg = viewModel.saveMessage
    LaunchedEffect(saveMsg) {
        saveMsg?.let { snackbarHostState.showSnackbar(it); viewModel.saveMessage = null }
    }
    // Refresh battery-optimization status on resume (user may fix it in system Settings).
    var isIgnoring by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                try { isIgnoring = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) } catch (_: Exception) {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, null, tint = c.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        bottomBar = {
            Surface(color = c.background, modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
                Button(
                    onClick = { viewModel.saveAll() },
                    enabled = !viewModel.isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                ) {
                    Text(if (viewModel.isSaving) "Saving…" else "Save", fontWeight = FontWeight.SemiBold, color = c.textOnPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = c.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {

            // Battery optimization card
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Battery optimization", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                        if (isIgnoring) Text("Unrestricted", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.success) else Text("Restricted", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.error)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (isIgnoring) "Background tracking will work reliably." else "Tap to allow background tracking — required for trip detection.", fontSize = 11.sp, color = c.textTertiary, lineHeight = 15.sp)
                    if (!isIgnoring) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(BatteryOptimizationHelper.getBatteryOptimizationIntent(context).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            } catch (e: Exception) {
                                android.util.Log.w("Settings", "battery intent failed", e)
                            }
                        }) { Text("Fix now", fontSize = 12.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(BatteryOptimizationHelper.getManufacturerSteps(), fontSize = 10.sp, color = c.textTertiary, lineHeight = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Quick links
            SectionHeader("MANAGE")
            GigCard(Modifier.fillMaxWidth()) {
                Column {
                    TextButton(onClick = onNavigateExpenses, modifier = Modifier.fillMaxWidth()) { Text("Expenses", modifier = Modifier.weight(1f), color = c.textPrimary); Text("→", color = c.textTertiary) }
                    HorizontalDivider(color = c.divider)
                    TextButton(onClick = onNavigateGoals, modifier = Modifier.fillMaxWidth()) { Text("Goals", modifier = Modifier.weight(1f), color = c.textPrimary); Text("→", color = c.textTertiary) }
                    HorizontalDivider(color = c.divider)
                    TextButton(onClick = onNavigateTax, modifier = Modifier.fillMaxWidth()) { Text("Tax helper", modifier = Modifier.weight(1f), color = c.textPrimary); Text("→", color = c.textTertiary) }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Theme
            SectionHeader("APPEARANCE")
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    // R&D fix: single hoisted selection — old code had `var selected`
                    // inside forEach so chips were never mutually exclusive.
                    var themeSelected by remember(viewModel.themeMode) { mutableStateOf(viewModel.themeMode.ifBlank { "system" }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            FilterChip(selected = themeSelected == value, onClick = {
                                themeSelected = value
                                viewModel.themeMode = value
                                onThemeChange(value)
                            }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader("LOCATION ANCHORS")
            GigCard(Modifier.fillMaxWidth()) {
                Column {
                    MinimalField("Home lat", viewModel.homeLat) { viewModel.homeLat = it }; DividerThin()
                    MinimalField("Home lon", viewModel.homeLon) { viewModel.homeLon = it }; DividerThin()
                    MinimalField("Store lat", viewModel.storeLat) { viewModel.storeLat = it }; DividerThin()
                    MinimalField("Store lon", viewModel.storeLon) { viewModel.storeLon = it }; DividerThin()
                    MinimalField("College lat", viewModel.collegeLat) { viewModel.collegeLat = it }; DividerThin()
                    MinimalField("College lon", viewModel.collegeLon) { viewModel.collegeLon = it }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader("VEHICLE")
            GigCard(Modifier.fillMaxWidth()) {
                Column {
                    MinimalField("Nickname", viewModel.vehicleName) { viewModel.vehicleName = it }; DividerThin()
                    MinimalField("Company", viewModel.vehicleCompany) { viewModel.vehicleCompany = it }; DividerThin()
                    MinimalField("Model", viewModel.vehicleModel) { viewModel.vehicleModel = it }; DividerThin()
                    MinimalField("Odometer", viewModel.odometer) { viewModel.odometer = it }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader("FUEL & COSTS")
            GigCard(Modifier.fillMaxWidth()) {
                Column {
                    MinimalField("Efficiency (km/L)", viewModel.fuelEfficiency) { viewModel.fuelEfficiency = it }; DividerThin()
                    MinimalField("Price (₹/L)", viewModel.fuelPrice) { viewModel.fuelPrice = it }; DividerThin()
                    MinimalField("Daily EMI (₹)", viewModel.dailyEmi) { viewModel.dailyEmi = it }; DividerThin()
                    MinimalField("Phone cost (₹)", viewModel.phoneCost) { viewModel.phoneCost = it }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader("SAFETY")
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Crash detection", fontSize = 13.sp, color = c.textPrimary)
                        Switch(checked = viewModel.crashEnabled, onCheckedChange = {
                            viewModel.crashEnabled = it
                            if (it && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                smsLauncher.launch(android.Manifest.permission.SEND_SMS)
                            }
                        })
                    }
                    // Fail-closed UX: an enabled toggle with denied SMS is a dead
                    // safety feature — say so persistently until granted.
                    if (viewModel.crashEnabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Text(
                            "SMS permission denied — crash alerts can't be sent. Enable it to arm detection.",
                            fontSize = 11.sp, color = c.error, lineHeight = 15.sp
                        )
                    }
                    if (viewModel.crashEnabled) {
                        MinimalField("G-force", viewModel.gForceThreshold) { viewModel.gForceThreshold = it }
                        MinimalField("Contact 1", viewModel.contact1) { viewModel.contact1 = it }
                        MinimalField("Contact 2", viewModel.contact2) { viewModel.contact2 = it }
                        MinimalField("Contact 3", viewModel.contact3) { viewModel.contact3 = it }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader("RIDING ASSIST")
            GigCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Speed alert", fontSize = 13.sp, color = c.textPrimary); Switch(checked = viewModel.speedAlertEnabled, onCheckedChange = { viewModel.speedAlertEnabled = it })
                    }
                    if (viewModel.speedAlertEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Limit", fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
                            // Keyed once on entry — never on the live field, or drags re-seed mid-gesture.
                            var slider by remember { mutableStateOf(viewModel.speedLimit.toFloatOrNull() ?: 80f) }
                            Slider(value = slider, onValueChange = { slider = it; viewModel.speedLimit = it.toInt().toString() }, valueRange = 20f..120f, modifier = Modifier.weight(2f))
                            Text("${slider.toInt()} km/h", fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalField(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalGigRunColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
        TextField(value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = c.textPrimary), colors = TextFieldDefaults.colors(focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant, focusedIndicatorColor = c.primary, unfocusedIndicatorColor = c.border))
    }
}

@Composable
private fun DividerThin() {
    HorizontalDivider(color = LocalGigRunColors.current.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 14.dp))
}
