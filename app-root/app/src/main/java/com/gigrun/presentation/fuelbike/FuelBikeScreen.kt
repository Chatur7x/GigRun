package com.gigrun.presentation.fuelbike

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.core.utils.LedgerManager
import com.gigrun.data.database.dao.FuelLogDao
import com.gigrun.data.database.dao.ServiceReminderDao
import com.gigrun.data.database.dao.VehicleDao
import com.gigrun.data.database.entities.Block
import com.gigrun.data.database.entities.FuelLog
import com.gigrun.data.database.entities.ServiceReminder
import com.gigrun.data.database.entities.Vehicle
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.ui.components.BreakEvenMeter
import com.gigrun.ui.components.ChipLabel
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class FuelBikeViewModel @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val fuelLogDao: FuelLogDao,
    private val blockDao: com.gigrun.data.database.dao.BlockDao,
    private val serviceReminderDao: ServiceReminderDao,
    private val ledgerManager: LedgerManager,
    private val prefs: UserPreferences
) : ViewModel() {

    val vehicles = vehicleDao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedVehicle = vehicleDao.getSelectedVehicle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminders = serviceReminderDao.getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockchainBlocks = ledgerManager.blocksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tempTransactions = ledgerManager.tempTransactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeFuelLog = flow {
        while (true) {
            try { emit(fuelLogDao.getActiveFuelLogSync()) } catch (e: Exception) {
                android.util.Log.w("FuelBikeVM", "active fuel poll failed", e)
            }
            kotlinx.coroutines.delay(10_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLedgerValid = flow {
        while (true) {
            try { emit(ledgerManager.verifyBlockchain()) } catch (e: Exception) {
                android.util.Log.w("FuelBikeVM", "ledger verify failed", e)
                emit(false)
            }
            kotlinx.coroutines.delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val accumulatedDistance = prefs.accumulatedDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val fuelEfficiency = prefs.fuelEfficiency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 45.0)

    val fuelPrice = prefs.fuelPrice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 103.0)

    val dailyFixedCosts = prefs.dailyFixedCosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    var isMining = MutableStateFlow(false)
    var activeShiftEarning = MutableStateFlow(0.0) // Mock shift earnings sum for break-even calc
    val fuelError = MutableStateFlow<String?>(null)
    private val selectMutex = kotlinx.coroutines.sync.Mutex()

    init {
        // Calculate mock stats
        viewModelScope.launch {
            activeShiftEarning.value = 850.0 // Default fallback
        }
    }

    fun selectVehicle(vehicleId: Long) {
        viewModelScope.launch {
            // Serialized: two concurrent selects interleaved deselect/set into two selected rows.
            selectMutex.withLock { vehicleDao.selectVehicle(vehicleId) }
        }
    }

    fun addVehicle(type: String, name: String, company: String, model: String, odometer: Double) {
        // Negative/huge odometers corrupt mileage math and the break-even gauge.
        val safeOdo = odometer.takeIf { it.isFinite() }?.coerceIn(0.0, 2_000_000.0) ?: return
        viewModelScope.launch {
            val newV = Vehicle(
                type = type.take(20),
                name = name.take(60).ifBlank { "My Vehicle" },
                company = company.take(60),
                model = model.take(60),
                currentOdometer = safeOdo
            )
            val id = vehicleDao.insert(newV)
            vehicleDao.selectVehicle(id)
        }
    }

    fun logPour(amountInr: Double, liters: Double) {
        val safeAmount = amountInr.takeIf { it.isFinite() }?.coerceIn(0.0, 50_000.0) ?: return
        val safeLiters = liters.takeIf { it.isFinite() }?.coerceIn(0.0, 500.0) ?: return
        if (safeAmount <= 0 || safeLiters <= 0) return
        viewModelScope.launch {
            val activeV = selectedVehicle.value ?: return@launch
            val timeLimit = System.currentTimeMillis() - (15 * 60 * 1000) // 15 mins
            val recentLog = fuelLogDao.getLatestFuelLogWithinTime(activeV.id, timeLimit)

            if (recentLog != null && !recentLog.isClosed) {
                // Merge poured amount & liters
                val merged = recentLog.copy(
                    amountInr = recentLog.amountInr + safeAmount,
                    liters = recentLog.liters + safeLiters,
                    timestamp = System.currentTimeMillis()
                )
                fuelLogDao.update(merged)
            } else {
                // Create new log
                val newLog = FuelLog(
                    vehicleId = activeV.id,
                    amountInr = safeAmount,
                    liters = safeLiters,
                    odometer = activeV.currentOdometer,
                    timestamp = System.currentTimeMillis()
                )
                val logId = fuelLogDao.insert(newLog)

                // Add to Blockchain Mempool
                val payload = Json.encodeToString(newLog.copy(id = logId))
                ledgerManager.addTransaction("FUEL", payload)
            }
        }
    }

    fun finalizeFuelLog(endingOdometer: Double) {
        viewModelScope.launch {
            fuelError.value = null
            val activeLog = activeFuelLog.value
            val activeV = selectedVehicle.value
            if (activeLog == null || activeV == null) {
                fuelError.value = "No active fuel batch — pour petrol first."
                return@launch
            }
            if (endingOdometer < activeLog.odometer) {
                fuelError.value = "Ending odometer can't be below ${activeLog.odometer.toInt()} km."
                return@launch
            }

            val distance = endingOdometer - activeLog.odometer
            val mileage = if (activeLog.liters > 0) distance / activeLog.liters else 0.0

            val closedLog = activeLog.copy(
                isClosed = true,
                closedOdometer = endingOdometer,
                calculatedMileage = mileage
            )
            fuelLogDao.update(closedLog)

            // Update user preferences & vehicle odo
            prefs.setFuelSettings(mileage, fuelPrice.value ?: 103.0)
            vehicleDao.update(activeV.copy(currentOdometer = endingOdometer))

            // Commit to Blockchain Mempool
            val payload = Json.encodeToString(closedLog)
            ledgerManager.addTransaction("FUEL_FINISHED", payload)
        }
    }

    fun commitToBlockchain() {
        viewModelScope.launch {
            isMining.value = true
            try {
                ledgerManager.commitTransactionsToBlockchain()
            } catch (e: Exception) {
                android.util.Log.e("FuelBikeVM", "mine failed", e)
            } finally {
                isMining.value = false
            }
        }
    }

    fun markReminderDone(reminder: ServiceReminder) {
        viewModelScope.launch {
            val activeV = selectedVehicle.value ?: return@launch
            serviceReminderDao.update(
                reminder.copy(
                    lastDoneKm = activeV.currentOdometer,
                    lastDoneDate = System.currentTimeMillis(),
                    isSnoozed = false,
                    snoozeUntil = null
                )
            )
        }
    }

    fun snoozeReminder(reminder: ServiceReminder) {
        viewModelScope.launch {
            serviceReminderDao.snoozeReminder(reminder.id, System.currentTimeMillis() + 3 * 86_400_000L)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelBikeScreen(
    viewModel: FuelBikeViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val context = LocalContext.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val blocks by viewModel.blockchainBlocks.collectAsState()
    val tempTransactions by viewModel.tempTransactions.collectAsState()
    val activeFuelLog by viewModel.activeFuelLog.collectAsState()
    val isLedgerValid by viewModel.isLedgerValid.collectAsState()

    val odoDistance by viewModel.accumulatedDistance.collectAsState()
    val mileageVal by viewModel.fuelEfficiency.collectAsState()
    val priceVal by viewModel.fuelPrice.collectAsState()
    val dailyCosts by viewModel.dailyFixedCosts.collectAsState()

    val isMining by viewModel.isMining.collectAsState()
    val activeShiftEarning by viewModel.activeShiftEarning.collectAsState()

    // Dialog state controllers
    var showAddVehicle by remember { mutableStateOf(false) }
    var showPourFuel by remember { mutableStateOf(false) }
    var showFinishFuel by remember { mutableStateOf(false) }
    var showDoneConfirm by remember { mutableStateOf<ServiceReminder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel & Bike", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, letterSpacing = (-0.41).sp) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        containerColor = c.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Vehicle Selector Carousel with 3D Parallax Hover ────────────────
            Text("MY VEHICLE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))

            VehicleCarousel(
                vehicles = vehicles,
                selectedVehicle = selectedVehicle,
                onSelect = { viewModel.selectVehicle(it) },
                onAdd = { showAddVehicle = true }
            )

            Spacer(Modifier.height(24.dp))

            // ── Break-Even Target circular gauge side-by-side with metrics ────────
            GigCard(
                modifier = Modifier.fillMaxWidth(),
                entranceIndex = 0,
                entranceVisible = entered
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Guard: zero/negative mileage or price settings → fall back to sane defaults.
                    val safeMileage = mileageVal?.takeIf { it > 0 } ?: 45.0
                    val safePrice = priceVal?.takeIf { it >= 0 } ?: 103.0
                    val breakEvenTarget = dailyCosts + (odoDistance * (1.0 / safeMileage) * safePrice)
                    BreakEvenMeter(
                        earned = activeShiftEarning,
                        breakEvenTarget = breakEvenTarget,
                        compact = true,
                        modifier = Modifier.size(110.dp)
                    )
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("Break-Even Target", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        StatRow("Fuel Price", "₹${safePrice.toInt()}/L")
                        StatRow("Mileage", "${safeMileage.toInt()} km/L")
                        StatRow("Daily Target", "₹${breakEvenTarget.toInt()}", valueColor = c.warning)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Active Fuel Batch Controls ───────────────────────────
            GigCard(
                modifier = Modifier.fillMaxWidth(),
                entranceIndex = 1,
                entranceVisible = entered
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current Fuel Batch", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    val fuelErr by viewModel.fuelError.collectAsState()
                    if (fuelErr != null) {
                        Text(fuelErr ?: "", fontSize = 12.sp, color = c.error, modifier = Modifier.padding(bottom = 8.dp))
                    }

                    if (activeFuelLog == null) {
                        Text(
                            "No active fuel logs. Pour petrol to start calibrating mileage.",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val log = activeFuelLog
                        if (log != null) {
                            StatRow("Start Odo", "${log.odometer.toInt()} km")
                            StatRow("Liters poured", "${String.format("%.2f", log.liters)} L")
                            StatRow("Poured cost", "₹${log.amountInr.toInt()}", valueColor = c.success)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showPourFuel = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                        ) {
                            Icon(Icons.Default.LocalGasStation, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pour Petrol")
                        }
                        if (activeFuelLog != null) {
                            Button(
                                onClick = { showFinishFuel = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.success)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fuel Finished")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Blockchain Audit Card ────────────────────────────────
            GigCard(
                modifier = Modifier.fillMaxWidth(),
                entranceIndex = 2,
                entranceVisible = entered
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GigChain Ledger", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        if (isLedgerValid) {
                            ChipLabel("Verified Secure", backgroundColor = c.success.copy(alpha = 0.15f), textColor = c.success)
                        } else {
                            ChipLabel("Warning: Tampered", backgroundColor = c.error.copy(alpha = 0.15f), textColor = c.error)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val lastBlock = blocks.firstOrNull()
                    val hashStr = lastBlock?.hash?.take(16) ?: "GENESIS_HASH"
                    StatRow("Last Mined Hash", "$hashStr...", valueColor = c.primary)
                    StatRow("Ledger Size", "${blocks.size} blocks")
                    StatRow("Mempool size", "${tempTransactions.size} logs", valueColor = c.warning)

                    Spacer(Modifier.height(14.dp))

                    if (tempTransactions.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.commitToBlockchain() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                        ) {
                            if (isMining) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Commit to Blockchain (Mine)")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Maintenance Reminders with Thicker Progress Bars ──────
            Text("MAINTENANCE HEALTH", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))

            if (reminders.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Build,
                    title = "No reminders set up yet",
                    subtitle = "Add your vehicle to start tracking maintenance health.",
                    actionLabel = "Add your vehicle",
                    onAction = { showAddVehicle = true },
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                reminders.forEachIndexed { index, reminder ->
                    val curOdo = selectedVehicle?.currentOdometer ?: 0.0
                    val kmSince = curOdo - reminder.lastDoneKm
                    val daysSince = ((System.currentTimeMillis() - reminder.lastDoneDate) / 86_400_000).toInt()
                    val kmProg = if (reminder.intervalKm < Double.MAX_VALUE / 2) (kmSince / reminder.intervalKm).coerceIn(0.0, 1.0).toFloat() else 0f
                    val dayProg = if (reminder.intervalDays > 0) (daysSince.toFloat() / reminder.intervalDays).coerceIn(0f, 1f) else 0f
                    val prog = maxOf(kmProg, dayProg)
                    val statusColor = when {
                        prog >= 0.9f -> c.error
                        prog >= 0.7f -> c.warning
                        else -> c.success
                    }
                    val icon = when (reminder.reminderType) {
                        "oil" -> Icons.Default.WaterDrop
                        "air_filter" -> Icons.Default.Air
                        "chain" -> Icons.Default.Link
                        "general" -> Icons.Default.Build
                        "tyre" -> Icons.Default.TireRepair
                        else -> Icons.Default.Settings
                    }
                    val title = when (reminder.reminderType) {
                        "oil" -> "Engine Oil"
                        "air_filter" -> "Air Filter"
                        "chain" -> "Chain Lube"
                        "general" -> "General Service"
                        "tyre" -> "Tyre Pressure"
                        else -> reminder.reminderType
                    }

                    GigCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        entranceIndex = index,
                        entranceVisible = entered
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                                    if (reminder.isSnoozed) Text("Snoozed", fontSize = 12.sp, color = c.warning)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // Thicker progress bar (8dp) with rounded caps
                            LinearProgressIndicator(
                                progress = { prog },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = statusColor,
                                trackColor = c.surfaceVariant,
                                drawStopIndicator = {}
                            )
                            Spacer(Modifier.height(12.dp))
                            if (reminder.intervalKm < Double.MAX_VALUE / 2) {
                                StatRow("Distance", "${kmSince.toInt()} / ${reminder.intervalKm.toInt()} km")
                            }
                            StatRow("Days elapsed", "$daysSince / ${reminder.intervalDays} days")
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.snoozeReminder(reminder) }) {
                                    Text("Snooze", color = c.textSecondary, fontSize = 15.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { showDoneConfirm = reminder },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.success)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Done", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Add Vehicle Form
    if (showAddVehicle) {
        var vType by remember { mutableStateOf("motorcycle") }
        var vName by remember { mutableStateOf("") }
        var vCompany by remember { mutableStateOf("") }
        var vModel by remember { mutableStateOf("") }
        var vOdo by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddVehicle = false },
            title = { Text("Add Vehicle", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vName, { vName = it }, label = { Text("Nickname (e.g. My Bike)") })
                    OutlinedTextField(vCompany, { vCompany = it }, label = { Text("Company (e.g. Honda)") })
                    OutlinedTextField(vModel, { vModel = it }, label = { Text("Model (e.g. Activa)") })
                    OutlinedTextField(vOdo, { vOdo = it }, label = { Text("Starting Odometer (km)") })

                    Text("Vehicle Type", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("motorcycle", "scooter", "auto", "bike", "car").forEach { type ->
                            FilterChip(
                                selected = vType == type,
                                onClick = { vType = type },
                                label = { Text(type.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addVehicle(vType, vName, vCompany, vModel, vOdo.toDoubleOrNull() ?: 0.0)
                        showAddVehicle = false
                    }
                ) {
                    Text("Add", fontWeight = FontWeight.Bold, color = c.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddVehicle = false }) {
                    Text("Cancel", color = c.error)
                }
            },
            containerColor = c.surfaceVariant
        )
    }

    // Dialog 2: Pour Fuel Dialog
    if (showPourFuel) {
        var modeAmount by remember { mutableStateOf(true) } // true for Rs, false for Liters
        var pourInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPourFuel = false },
            title = { Text("Pour Fuel", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Input Mode", fontSize = 13.sp, color = c.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = modeAmount, onClick = { modeAmount = true; pourInput = "" }, label = { Text("Amount (₹)") })
                        FilterChip(selected = !modeAmount, onClick = { modeAmount = false; pourInput = "" }, label = { Text("Volume (L)") })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pourInput,
                        onValueChange = { pourInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(if (modeAmount) "Amount in ₹" else "Volume in Litres") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val inputVal = pourInput.toDoubleOrNull() ?: 0.0
                        if (inputVal > 0) {
                            // Guard: zero fuel price → can't convert amount to liters.
                            val activePrice = priceVal?.takeIf { it > 0 } ?: 103.0
                            val (finalAmount, finalLiters) = if (modeAmount) {
                                Pair(inputVal, inputVal / activePrice)
                            } else {
                                Pair(inputVal * activePrice, inputVal)
                            }
                            viewModel.logPour(finalAmount, finalLiters)
                        }
                        showPourFuel = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = c.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPourFuel = false }) {
                    Text("Cancel", color = c.error)
                }
            },
            containerColor = c.surfaceVariant
        )
    }

    // Dialog 3: Fuel Finished Calibration
    if (showFinishFuel) {
        var endingOdoInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFinishFuel = false },
            title = { Text("Fuel Finished", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Column {
                    Text("Enter the ending odometer reading to calibrate vehicle mileage:", fontSize = 13.sp, color = c.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = endingOdoInput,
                        onValueChange = { endingOdoInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Ending Odometer (km)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        endingOdoInput.toDoubleOrNull()?.let {
                            viewModel.finalizeFuelLog(it)
                        }
                        showFinishFuel = false
                    }
                ) {
                    Text("Calibrate", fontWeight = FontWeight.Bold, color = c.success)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishFuel = false }) {
                    Text("Cancel", color = c.error)
                }
            },
            containerColor = c.surfaceVariant
        )
    }

    // Dialog 4: Confirm Done Maintenance
    if (showDoneConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDoneConfirm = null },
            title = { Text("Mark Service as Done?", fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = { Text("This will reset the tracker mileage and days for this item.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDoneConfirm?.let { viewModel.markReminderDone(it) }
                        showDoneConfirm = null
                    }
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold, color = c.success)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoneConfirm = null }) {
                    Text("Cancel", color = c.error)
                }
            },
            containerColor = c.surfaceVariant
        )
    }
}

/**
 * Gyro-tilt vehicle carousel — sensor state lives here so 3D tilt updates
 * recompose ONLY this node, never the whole FuelBike screen.
 */
@Composable
private fun VehicleCarousel(
    vehicles: List<Vehicle>,
    selectedVehicle: Vehicle?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit
) {
    val c = LocalGigRunColors.current
    val context = LocalContext.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationVectorSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var rotationX by remember { mutableFloatStateOf(0f) }
    var rotationY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val v = event.values
                    if (v == null || v.size < 4) return
                    val rotMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotMatrix, v)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotMatrix, orientation)
                    rotationX = (orientation[1] * 57.29578f).coerceIn(-12f, 12f)
                    rotationY = (orientation[2] * 57.29578f).coerceIn(-12f, 12f)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    if (vehicles.isEmpty()) {
        GigCard(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            entranceIndex = 0,
            entranceVisible = entered
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, null, tint = c.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Add Your Vehicle", color = c.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            vehicles.forEachIndexed { index, v ->
                val isSel = selectedVehicle?.id == v.id
                GigCard(
                    onClick = { onSelect(v.id) },
                    modifier = Modifier.width(220.dp).height(130.dp)
                        .graphicsLayer {
                            if (isSel) {
                                this.rotationX = -rotationX
                                this.rotationY = rotationY
                                this.cameraDistance = 12f * density
                            }
                        }
                        .shadow(elevation = if (isSel) 8.dp else 2.dp, shape = RoundedCornerShape(14.dp)),
                    entranceIndex = index,
                    entranceVisible = entered
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (v.type.lowercase()) {
                                "car" -> Icons.Default.DirectionsCar
                                "bike" -> Icons.Default.DirectionsBike
                                "auto" -> Icons.Default.ElectricRickshaw
                                "scooter" -> Icons.Default.Moped
                                else -> Icons.Default.TwoWheeler
                            }
                            Icon(imageVector = icon, contentDescription = null, tint = if (isSel) c.primary else c.textSecondary, modifier = Modifier.size(32.dp))
                            if (isSel) Icon(Icons.Default.CheckCircle, null, tint = c.success, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(v.name, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 17.sp)
                        Text("${v.company} ${v.model}  ·  ${v.currentOdometer.toInt()} km", color = c.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            GigCard(
                onClick = onAdd,
                modifier = Modifier.width(80.dp).height(130.dp),
                entranceIndex = vehicles.size,
                entranceVisible = entered
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = c.textSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
