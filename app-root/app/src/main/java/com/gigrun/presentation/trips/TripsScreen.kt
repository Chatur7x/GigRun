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
import com.gigrun.ui.theme.Apple
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
class TripsViewModel @Inject constructor(private val tripDao: TripDao) : ViewModel() {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()
    private val _selectedTrip = MutableStateFlow<Trip?>(null)
    val selectedTrip: StateFlow<Trip?> = _selectedTrip.asStateFlow()
    private var loadJob: Job? = null
    init { loadToday() }
    fun loadToday() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cal = Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            tripDao.getTripsSince(cal.timeInMillis).collect { _trips.value = it }
        }
    }
    fun selectTrip(trip: Trip?) { _selectedTrip.value = trip }
}

@Composable
fun TripListScreen(viewModel: TripsViewModel = hiltViewModel(), onTripClick: (Trip) -> Unit = {}) {
    val c = Apple.colors
    val trips by viewModel.trips.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(c.groupedBackground).padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Text("Trips", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.padding(vertical = 8.dp))

        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.TwoWheeler, null, tint = c.gray, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Trips Today", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                    Spacer(Modifier.height(6.dp))
                    Text("Start a shift to begin tracking.", fontSize = 15.sp, color = c.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
        } else {
            // Grouped card for all trips
            Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.fillMaxWidth()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(trips) { trip ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.selectTrip(trip); onTripClick(trip) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PlatformBadge(trip.platform)
                                        Spacer(Modifier.width(10.dp))
                                        Text("${sdf.format(Date(trip.startTime))}${trip.endTime?.let { " → ${sdf.format(Date(it))}" } ?: ""}", fontSize = 13.sp, color = c.secondaryLabel)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("${String.format("%.1f", trip.distanceKm)} km  ·  ${trip.waitTimeSec / 60}m wait", fontSize = 13.sp, color = c.tertiaryLabel)
                                }
                                trip.earningInr?.let { Text("₹${it.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.green) }
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.ChevronRight, null, tint = c.gray3, modifier = Modifier.size(20.dp))
                            }
                            // Inset divider (not on last item)
                            if (trip != trips.last()) HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripDetailScreen(viewModel: TripsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val c = Apple.colors
    val trip by viewModel.selectedTrip.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    trip?.let { t ->
        val pathPoints = remember(t.pathEncoded) {
            t.pathEncoded?.let { try { PolylineEncoder.decode(it).map { p -> LatLng(p.first, p.second) } } catch (_: Exception) { emptyList() } } ?: emptyList()
        }

        Column(Modifier.fillMaxSize().background(c.groupedBackground).padding(bottom = 100.dp)) {
            // Nav bar
            Surface(color = c.secondaryGroupedBackground) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.blue, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Trips", color = c.blue, fontSize = 17.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Trip Detail", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(80.dp))
                }
            }

            if (pathPoints.size >= 2) {
                val center = pathPoints[pathPoints.size / 2]
                val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, 15f) }
                GoogleMap(Modifier.fillMaxWidth().height(240.dp), cameraPositionState = cameraPositionState, uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)) {
                    Polyline(points = pathPoints, color = c.blue, width = 6f)
                    Marker(state = MarkerState(position = pathPoints.first()), title = "Start")
                    Marker(state = MarkerState(position = pathPoints.last()), title = "End")
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformBadge(t.platform)
                        Spacer(Modifier.weight(1f))
                        t.earningInr?.let { Text("₹${it.toInt()}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.green) }
                    }
                    Spacer(Modifier.height(20.dp))
                    StatRow("Start", sdf.format(Date(t.startTime)))
                    HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    t.endTime?.let { StatRow("End", sdf.format(Date(it))); HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)) }
                    StatRow("Distance", "${String.format("%.2f", t.distanceKm)} km")
                    HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    StatRow("Wait", "${t.waitTimeSec / 60}m ${t.waitTimeSec % 60}s", valueColor = c.orange)
                    HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    StatRow("From", "${String.format("%.4f", t.startLat)}, ${String.format("%.4f", t.startLon)}")
                    t.endLat?.let { lat -> t.endLon?.let { lon -> HorizontalDivider(color = c.opaqueSeparator, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)); StatRow("To", "${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}") } }
                }
            }
        }
    } ?: Box(Modifier.fillMaxSize().background(c.groupedBackground), contentAlignment = Alignment.Center) { Text("No trip selected", color = c.secondaryLabel) }
}
