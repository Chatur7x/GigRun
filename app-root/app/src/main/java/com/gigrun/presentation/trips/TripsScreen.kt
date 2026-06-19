package com.gigrun.presentation.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.gigrun.core.utils.PolylineEncoder
import com.gigrun.data.database.dao.TripDao
import com.gigrun.data.database.entities.Trip
import com.gigrun.ui.components.PlatformBadge
import com.gigrun.ui.components.StatRow
import com.gigrun.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripDao: TripDao
) : ViewModel() {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip.asStateFlow()

    private var loadJob: Job? = null

    init { loadToday() }

    fun loadToday() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val start = cal.timeInMillis
            tripDao.getTripsSince(start).collect { _trips.value = it }
        }
    }

    fun selectTrip(trip: Trip?) { _selectedTrip.value = trip }
}

@Composable
fun TripListScreen(viewModel: TripsViewModel = hiltViewModel(), onTripClick: (Trip) -> Unit = {}) {
    val trips by viewModel.trips.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(DeepCarbon).padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text("TRIPS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text("Today's Rides", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(16.dp))

        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No trips yet today.\nStart a shift to begin tracking.", color = TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(trips) { trip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTrip(trip); onTripClick(trip) }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TwoWheeler, null, tint = CyberCyan, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PlatformBadge(trip.platform)
                                    Spacer(Modifier.width(8.dp))
                                    Text(sdf.format(Date(trip.startTime)), fontSize = 13.sp, color = TextSecondary)
                                    trip.endTime?.let { Text(" → ${sdf.format(Date(it))}", fontSize = 13.sp, color = TextSecondary) }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${String.format("%.1f", trip.distanceKm)} km · Wait ${trip.waitTimeSec / 60}m", fontSize = 12.sp, color = TextSecondary)
                            }
                            trip.earningInr?.let {
                                Text("₹${it.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripDetailScreen(viewModel: TripsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val trip by viewModel.selectedTrip.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    trip?.let { t ->
        // Decode polyline path for map display
        val pathPoints = remember(t.pathEncoded) {
            t.pathEncoded?.let { encoded ->
                try {
                    PolylineEncoder.decode(encoded).map { LatLng(it.first, it.second) }
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }

        Column(Modifier.fillMaxSize().background(DeepCarbon).padding(top = 16.dp, bottom = 100.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CyberCyan) }
                Text("Trip Detail", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(12.dp))

            // Map with polyline
            if (pathPoints.size >= 2) {
                val center = pathPoints[pathPoints.size / 2]
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(center, 15f)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 20.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
                    ) {
                        Polyline(
                            points = pathPoints,
                            color = CyberCyan,
                            width = 8f
                        )
                        Marker(
                            state = MarkerState(position = pathPoints.first()),
                            title = "Start"
                        )
                        Marker(
                            state = MarkerState(position = pathPoints.last()),
                            title = "End"
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Trip details card
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformBadge(t.platform)
                        Spacer(Modifier.width(10.dp))
                        t.earningInr?.let {
                            Text("₹${it.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    StatRow("Start Time", sdf.format(Date(t.startTime)))
                    t.endTime?.let { StatRow("End Time", sdf.format(Date(it))) }
                    StatRow("Distance", "${String.format("%.2f", t.distanceKm)} km")
                    StatRow("Wait Time", "${t.waitTimeSec / 60} min ${t.waitTimeSec % 60} sec", valueColor = MoltenAmber)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DividerColor)
                    StatRow("Start Coordinates", "${String.format("%.4f", t.startLat)}, ${String.format("%.4f", t.startLon)}")
                    t.endLat?.let { lat -> t.endLon?.let { lon -> StatRow("End Coordinates", "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}") } }
                }
            }
        }
    } ?: Box(Modifier.fillMaxSize().background(DeepCarbon), contentAlignment = Alignment.Center) { Text("No trip selected", color = TextSecondary) }
}
