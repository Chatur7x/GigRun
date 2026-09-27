package com.gigrun.presentation.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.gigrun.core.utils.OcrProcessor
import com.gigrun.core.utils.OcrResult
import com.gigrun.core.utils.PolylineEncoder
import com.gigrun.core.utils.LedgerManager
import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.database.entities.Trip
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.components.PlatformBadge
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.design.LocalGigRunColors
import com.gigrun.ui.theme.AppleMapStyle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/** Canonical platform names — scanner allow-list and chip list stay in sync. */
val KNOWN_PLATFORMS = listOf("Uber", "Rapido", "Blinkit", "Zepto", "Swiggy", "Zomato", "BigBasket", "untagged")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripDao: TripDao,
    private val shiftDao: com.gigrun.data.database.dao.ShiftDao,
    private val prefs: UserPreferences,
    private val ledgerManager: LedgerManager
) : ViewModel() {

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip.asStateFlow()

    val enabledPlatforms: StateFlow<List<String>> = prefs.enabledPlatforms
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI filters
    val selectedTimeFilter = MutableStateFlow("Day") // "Day", "Week", "Month"
    val selectedPlatformFilter = MutableStateFlow("All") // "All" or specific

    // OCR screenshot scanner state
    val ocrResult = MutableStateFlow<OcrResult?>(null)
    val isScanning = MutableStateFlow(false)
    val scanError = MutableStateFlow<String?>(null)
    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // R&D fix: old code launched a new DAO collector per filter change without
        // cancelling the previous one (leak + duplicate emissions). Use flatMapLatest.
        // Time + platform fully determine the query — no blind third trigger.
        viewModelScope.launch {
            combine(selectedTimeFilter, selectedPlatformFilter) { timeFilter, platformFilter ->
                timeFilter to platformFilter
            }.flatMapLatest { (timeFilter, platformFilter) ->
                val startTime = getFilterStartTime(timeFilter)
                tripDao.getTripsSince(startTime).map { allTrips ->
                    allTrips.filter { trip ->
                        platformFilter == "All" || trip.platform.equals(platformFilter, ignoreCase = true)
                    }
                }
            }.collect { _trips.value = it }
        }
    }

    private fun getFilterStartTime(filter: String): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (filter) {
            "Week" -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            "Month" -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    fun togglePlatform(platform: String) {
        viewModelScope.launch {
            val current = enabledPlatforms.value.toMutableList()
            if (current.contains(platform)) {
                current.remove(platform)
            } else {
                current.add(platform)
            }
            prefs.setEnabledPlatforms(current)
        }
    }

    fun selectTrip(trip: Trip?) {
        _selectedTrip.value = trip
    }

    /** Deep-link/process-death path: load a trip by id when selection is empty. */
    fun loadTripIfNeeded(tripId: Long?) {
        // Non-positive ids can never exist (autoincrement) — fail fast, no query.
        if (tripId == null || tripId <= 0 || _selectedTrip.value?.id == tripId) return
        viewModelScope.launch {
            try {
                _selectedTrip.value = tripDao.getTripById(tripId)
            } catch (e: Exception) {
                android.util.Log.e("TripsVM", "loadTrip failed", e)
            }
        }
    }

    fun processScreenshot(context: Context, uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            isScanning.value = true
            scanError.value = null
            try {
                // Single capped read (30 MB): a hostile content provider serving an
                // infinite stream used to stall decodeStream past cancellation.
                // Bytes feed bounds-check, EXIF rotation AND decode — one open.
                val bytes = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 30 * 1024 * 1024) break
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    }
                } catch (e: Exception) { null }
                if (bytes == null || bytes.isEmpty()) {
                    scanError.value = "Couldn't read that image. Try another screenshot."
                    return@launch
                }
                // EXIF rotation: portrait shots with Orientation=6 decoded sideways
                // and OCR returned garbage the user had to hand-fix.
                val rotation = try {
                    val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                    when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) { 0 }
                // Two-pass decode capped at 2048px: a 100MP panorama picked as a
                // "screenshot" used to OOM the app (full-res decode + ML Kit).
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / sample > 2048 && sample < 16) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } catch (e: OutOfMemoryError) {
                    null
                }
                if (bitmap != null) {
                    try {
                        val result = OcrProcessor.processScreenshot(bitmap, rotation)
                        ocrResult.value = result
                    } finally {
                        try { if (!bitmap.isRecycled) bitmap.recycle() } catch (_: Exception) {}
                    }
                } else {
                    scanError.value = "Couldn't read that image. Try another screenshot."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                scanError.value = "Scan failed. Try again."
            } finally {
                isScanning.value = false
            }
        }
    }

    fun confirmScanResult(earning: Double, distance: Double, tripsCount: Int, platform: String) {
        viewModelScope.launch {
            ocrResult.value = null
            // Attach to the real active shift — never a hardcoded id.
            val activeShift = try { shiftDao.getActiveShift()?.id } catch (_: Exception) { null }
            if (activeShift == null) {
                scanError.value = "No active shift — start a shift first, then scan."
                return@launch
            }
            // Clamp hostile/hand-typed values: negatives corrupt SUM aggregates,
            // absurd magnitudes poison tax/PDF exports. Platform allow-listed.
            val safeEarning = earning.takeIf { it.isFinite() }?.coerceIn(0.0, 10_000_000.0) ?: 0.0
            val safeDistance = distance.takeIf { it.isFinite() }?.coerceIn(0.0, 2000.0) ?: 0.0
            val safeTrips = tripsCount.coerceIn(1, 500)
            val safePlatform = platform.takeIf { KNOWN_PLATFORMS.contains(it) } ?: "untagged"

            // Log trip(s)
            val cal = Calendar.getInstance()
            // Honor the scanned trip count: split totals across N rows instead of
            // silently dropping tripsCount (old code always wrote exactly one).
            val perEarning = safeEarning / safeTrips
            val perDistance = safeDistance / safeTrips
            repeat(safeTrips) { i ->
                val newTrip = Trip(
                    shiftId = activeShift,
                    platform = safePlatform,
                    startTime = cal.timeInMillis - (60000 * 30 * (safeTrips - i)),
                    endTime = cal.timeInMillis - (60000 * 30 * (safeTrips - i - 1)),
                    startLat = 12.9716, // Default Bangalore Lat
                    startLon = 77.5946, // Default Bangalore Lon
                    endLat = 12.9816,
                    endLon = 77.6046,
                    distanceKm = perDistance,
                    waitTimeSec = 300,
                    earningInr = perEarning
                )
                val tripId = tripDao.insert(newTrip)

                // Commit to Blockchain Mempool
                val payload = Json.encodeToString(newTrip.copy(id = tripId))
                ledgerManager.addTransaction("TRIP", payload)
            }
        }
    }

    fun cancelScanResult() {
        scanJob?.cancel()
        scanJob = null
        ocrResult.value = null
        scanError.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripsViewModel = hiltViewModel(),
    onTripClick: (Trip) -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val context = LocalContext.current
    val trips by viewModel.trips.collectAsState()
    val enabledPlatforms by viewModel.enabledPlatforms.collectAsState()
    val timeFilter by viewModel.selectedTimeFilter.collectAsState()
    val platformFilter by viewModel.selectedPlatformFilter.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanError by viewModel.scanError.collectAsState()

    var showOcrDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Trip Log") } // "Trip Log" or "Platform Compare"
    // Beast screen-enter (2-moment rule: header summary is the moment; rows use layout spring)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // State-machine safety: never leave the dialog flag stranded with nothing to show
    // (e.g. result consumed elsewhere) — collapse to closed instead of a blank scrim.
    if (showOcrDialog && ocrResult == null && scanError == null && !isScanning) {
        LaunchedEffect(Unit) { showOcrDialog = false }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.processScreenshot(context, it)
            showOcrDialog = true
        }
    }

    val platformsList = KNOWN_PLATFORMS.filter { it != "untagged" }
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Gigs",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        letterSpacing = (-0.41).sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.background
                )
            )
        },
        containerColor = c.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Horizontal Platform Selector ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platformsList.forEach { p ->
                    val isEnabled = enabledPlatforms.contains(p)
                    FilterChip(
                        selected = isEnabled,
                        onClick = { viewModel.togglePlatform(p) },
                        label = { Text(p, fontSize = 13.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.primary.copy(alpha = 0.15f),
                            selectedLabelColor = c.primary,
                            containerColor = c.surfaceVariant,
                            labelColor = c.textSecondary
                        )
                    )
                }
            }

            // ── Top Toggle Tab (Logs vs Platform Compare) ───────
            TabRow(
                selectedTabIndex = if (activeTab == "Trip Log") 0 else 1,
                containerColor = c.background,
                contentColor = c.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (activeTab == "Trip Log") 0 else 1]),
                        color = c.primary
                    )
                }
            ) {
                Tab(
                    selected = activeTab == "Trip Log",
                    onClick = { activeTab = "Trip Log" },
                    text = { Text("Trip Log", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == "Platform Compare",
                    onClick = { activeTab = "Platform Compare" },
                    text = { Text("Platform Compare", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Main Content based on Toggle ──────────────────
            if (activeTab == "Trip Log") {
                // Filters Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var showTimeMenu by remember { mutableStateOf(false) }
                    var showPlatformMenu by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showTimeMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(timeFilter, color = c.textPrimary)
                            Icon(Icons.Default.ArrowDropDown, null, tint = c.textPrimary)
                        }
                        DropdownMenu(
                            expanded = showTimeMenu,
                            onDismissRequest = { showTimeMenu = false }
                        ) {
                            listOf("Day", "Week", "Month").forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.selectedTimeFilter.value = item
                                        showTimeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showPlatformMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(platformFilter, color = c.textPrimary)
                            Icon(Icons.Default.ArrowDropDown, null, tint = c.textPrimary)
                        }
                        DropdownMenu(
                            expanded = showPlatformMenu,
                            onDismissRequest = { showPlatformMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    viewModel.selectedPlatformFilter.value = "All"
                                    showPlatformMenu = false
                                }
                            )
                            enabledPlatforms.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        viewModel.selectedPlatformFilter.value = item
                                        showPlatformMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Summary stats
                val totalPayout = trips.sumOf { it.earningInr ?: 0.0 }
                val totalDist = trips.sumOf { it.distanceKm }
                val totalCount = trips.size

                // Beast summary entrance (single moment; rows use layout spring)
                val summaryAlpha = com.gigrun.ui.design.beastEntranceAlpha(0, entered)
                val summaryY = com.gigrun.ui.design.beastEntranceOffsetY(0, entered)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .graphicsLayer {
                            alpha = summaryAlpha
                            translationY = summaryY
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Payout", fontSize = 11.sp, color = c.textSecondary)
                            Text("₹${totalPayout.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.success)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Trips count", fontSize = 11.sp, color = c.textSecondary)
                            Text("$totalCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distance", fontSize = 11.sp, color = c.textSecondary)
                            Text("${String.format("%.1f", totalDist)} km", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.primary)
                        }
                    }
                }

                // Screenshot scanner
                Button(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan summary screenshot to log")
                }

                // Logs list
                if (trips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No trips recorded for the selected filter.",
                                color = c.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { launcher.launch("image/*") }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan screenshot instead")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(trips, key = { it.id }) { trip ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .animateItem(
                                        fadeInSpec = androidx.compose.animation.core.tween(250),
                                        fadeOutSpec = androidx.compose.animation.core.tween(200),
                                        placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = c.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectTrip(trip)
                                            onTripClick(trip)
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PlatformBadge(trip.platform)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = sdf.format(Date(trip.startTime)),
                                                fontSize = 13.sp,
                                                color = c.textSecondary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${String.format("%.1f", trip.distanceKm)} km  ·  ${trip.waitTimeSec / 60}m wait",
                                            fontSize = 12.sp,
                                            color = c.textTertiary
                                        )
                                    }
                                    trip.earningInr?.let {
                                        Text(
                                            text = "₹${it.toInt()}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.success
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = c.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }

            } else {
                // Platform Compare View
                val platformsData = remember(trips) {
                    trips.groupBy { it.platform }.map { (p, tList) ->
                        val earned = tList.sumOf { it.earningInr ?: 0.0 }
                        val distance = tList.sumOf { it.distanceKm }
                        val avgWait = tList.map { it.waitTimeSec }.average().takeIf { !it.isNaN() } ?: 0.0
                        OcrResult(p, earned, distance, tList.size)
                    }.sortedByDescending { it.earnings }
                }

                if (platformsData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No platform comparison data yet.",
                                color = c.textSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { launcher.launch("image/*") }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan a summary screenshot")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        platformsData.forEachIndexed { index, pData ->
                            // Taste emoji policy: rank as text, not medal glyphs.
                            val rank = when (index) {
                                0 -> "1st"
                                1 -> "2nd"
                                2 -> "3rd"
                                else -> "${index + 1}th"
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = c.surfaceVariant
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PlatformBadge(pData.platform)
                                        Spacer(Modifier.width(6.dp))
                                        Text(rank, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.warning)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "${pData.trips} trips",
                                            fontSize = 13.sp,
                                            color = c.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "₹${pData.earnings.toInt()}",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.success
                                            )
                                            Text("Earned", fontSize = 11.sp, color = c.textTertiary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "${String.format("%.1f", pData.distanceKm)} km",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.primary
                                            )
                                            Text("Dist", fontSize = 11.sp, color = c.textTertiary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val waitMin = (pData.trips.toDouble().takeIf { it > 0 }?.let { pData.earnings / it } ?: 0.0).toInt()
                                            Text(
                                                "₹$waitMin/trip",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.warning
                                            )
                                            Text("Avg payout", fontSize = 11.sp, color = c.textTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // OCR dialog fields keyed on the scan result — a second scan never shows stale edits.
    if (showOcrDialog) {
        scanError?.let { err ->
            AlertDialog(
                onDismissRequest = { showOcrDialog = false; viewModel.cancelScanResult() },
                title = { Text("Scan failed", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                text = { Text(err, fontSize = 13.sp, color = c.textSecondary) },
                confirmButton = {
                    TextButton(onClick = { showOcrDialog = false; viewModel.cancelScanResult() }) {
                        Text("OK", fontWeight = FontWeight.Bold, color = c.primary)
                    }
                },
                containerColor = c.surfaceVariant
            )
        } ?: ocrResult?.let { ocr ->
            var manualEarning by remember(ocr) { mutableStateOf(ocr.earnings.toString()) }
            var manualDistance by remember(ocr) { mutableStateOf(ocr.distanceKm.toString()) }
            var manualTrips by remember(ocr) { mutableStateOf(ocr.trips.toString()) }
            var manualPlatform by remember(ocr) { mutableStateOf(ocr.platform) }

            AlertDialog(
                onDismissRequest = {
                    showOcrDialog = false
                    viewModel.cancelScanResult()
                },
                title = { Text("Confirm Scanned Trip", fontWeight = FontWeight.Bold, color = c.textPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scanned details from screenshot. Modify if incorrect:", fontSize = 13.sp, color = c.textSecondary)
                        
                        OutlinedTextField(
                            value = manualPlatform,
                            onValueChange = { manualPlatform = it },
                            label = { Text("Platform") }
                        )
                        OutlinedTextField(
                            value = manualEarning,
                            onValueChange = { manualEarning = it },
                            label = { Text("Earning (₹)") }
                        )
                        OutlinedTextField(
                            value = manualDistance,
                            onValueChange = { manualDistance = it },
                            label = { Text("Distance (km)") }
                        )
                        OutlinedTextField(
                            value = manualTrips,
                            onValueChange = { manualTrips = it },
                            label = { Text("Trips count") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.confirmScanResult(
                                manualEarning.toDoubleOrNull() ?: 0.0,
                                manualDistance.toDoubleOrNull() ?: 0.0,
                                manualTrips.toIntOrNull() ?: 1,
                                manualPlatform
                            )
                            showOcrDialog = false
                        }
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold, color = c.primary)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showOcrDialog = false
                            viewModel.cancelScanResult()
                        }
                    ) {
                        Text("Cancel", color = c.error)
                    }
                },
                containerColor = c.surfaceVariant
            )
        } ?: run {
            if (isScanning) {
                AlertDialog(
                    onDismissRequest = {
                        showOcrDialog = false
                        viewModel.cancelScanResult()
                    },
                    title = { Text("Scanning Screenshot...") },
                    text = { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.primary) } },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = {
                            showOcrDialog = false
                            viewModel.cancelScanResult()
                        }) { Text("Cancel", color = c.error) }
                    },
                    containerColor = c.surfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripsViewModel = hiltViewModel(),
    tripId: Long? = null,
    onBack: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    val trip by viewModel.selectedTrip.collectAsState()
    val sdf = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    LaunchedEffect(tripId) { viewModel.loadTripIfNeeded(tripId) }

    // Null trap fix: always render a Scaffold with back — never a bare fullscreen box.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Detail", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        },
        containerColor = c.background
    ) { padding ->
        val t = trip
        if (t == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No trip selected", color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                        Text("Back to trips")
                    }
                }
            }
            return@Scaffold
        }
        TripDetailContent(t, sdf, padding)
    }
}

@Composable
private fun TripDetailContent(
    t: com.gigrun.data.database.entities.Trip,
    sdf: SimpleDateFormat,
    padding: PaddingValues
) {
    val c = LocalGigRunColors.current
    val pathPoints = remember(t.pathEncoded) {
        t.pathEncoded?.let {
            try {
                PolylineEncoder.decode(it).map { p -> LatLng(p.first, p.second) }
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
                // Apple Map Style Render
                if (pathPoints.size >= 2) {
                    val center = pathPoints[pathPoints.size / 2]
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(center, 14f)
                    }
                    val mapProperties = MapProperties(
                        mapStyleOptions = MapStyleOptions(AppleMapStyle.json)
                    )
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        cameraPositionState = cameraPositionState,
                        properties = mapProperties,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
                    ) {
                        val startMarker = pathPoints.firstOrNull()
                        val endMarker = pathPoints.lastOrNull()
                        if (startMarker != null && endMarker != null) {
                            Polyline(points = pathPoints, color = c.primary, width = 8f)
                            val startMarkerState = remember { MarkerState(position = startMarker) }
                            val endMarkerState = remember { MarkerState(position = endMarker) }
                            Marker(state = startMarkerState, title = "Start")
                            Marker(state = endMarkerState, title = "End")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = c.surfaceVariant
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlatformBadge(t.platform)
                            Spacer(Modifier.weight(1f))
                            t.earningInr?.let {
                                Text("₹${it.toInt()}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.success)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        StatRow("Start", sdf.format(Date(t.startTime)), icon = Icons.Default.PlayArrow)
                        HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                        t.endTime?.let {
                            StatRow("End", sdf.format(Date(it)), icon = Icons.Default.Stop)
                            HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                        }
                        StatRow("Distance", "${String.format("%.2f", t.distanceKm)} km", icon = Icons.Default.Navigation)
                        HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                        StatRow("Wait time", "${t.waitTimeSec / 60}m ${t.waitTimeSec % 60}s", valueColor = c.warning, icon = Icons.Default.Pause)
                        HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                        StatRow("From", "${String.format("%.4f", t.startLat)}, ${String.format("%.4f", t.startLon)}", icon = Icons.Default.LocationOn)
                        t.endLat?.let { lat ->
                            t.endLon?.let { lon ->
                                HorizontalDivider(color = c.divider, thickness = 0.5.dp)
                                StatRow("To", "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}", icon = Icons.Default.Flag)
                            }
                        }
                    }
                }
    }
}
