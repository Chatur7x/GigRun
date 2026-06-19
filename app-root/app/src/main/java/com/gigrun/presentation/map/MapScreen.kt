package com.gigrun.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.ui.theme.Apple
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {
    var homeAnchor by mutableStateOf<LatLng?>(null); var storeAnchor by mutableStateOf<LatLng?>(null); var collegeAnchor by mutableStateOf<LatLng?>(null); var isLoaded by mutableStateOf(false)
    init { viewModelScope.launch { prefs.homeAnchor.first()?.let { homeAnchor = LatLng(it.first, it.second) }; prefs.storeAnchor.first()?.let { storeAnchor = LatLng(it.first, it.second) }; prefs.collegeAnchor.first()?.let { collegeAnchor = LatLng(it.first, it.second) }; isLoaded = true } }
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val c = Apple.colors
    val context = LocalContext.current

    if (!viewModel.isLoaded) { Box(Modifier.fillMaxSize().background(c.groupedBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }; return }

    val hasAnchors = viewModel.homeAnchor != null || viewModel.storeAnchor != null || viewModel.collegeAnchor != null
    if (!hasAnchors) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground, modifier = Modifier.padding(32.dp)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Map, null, tint = c.blue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Locations", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                    Spacer(Modifier.height(6.dp))
                    Text("Add coordinates in Settings.", fontSize = 15.sp, color = c.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
        }; return
    }

    val defaultCenter = viewModel.homeAnchor ?: viewModel.storeAnchor ?: viewModel.collegeAnchor ?: LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultCenter, 14f) }
    val hasLoc = remember { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, properties = MapProperties(isMyLocationEnabled = hasLoc), uiSettings = MapUiSettings(myLocationButtonEnabled = hasLoc, zoomControlsEnabled = false, compassEnabled = true)) {
            viewModel.homeAnchor?.let { Marker(MarkerState(it), title = "Home") }
            viewModel.storeAnchor?.let { Marker(MarkerState(it), title = "Store / Hub") }
            viewModel.collegeAnchor?.let { Marker(MarkerState(it), title = "College") }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = c.secondaryGroupedBackground.copy(alpha = 0.92f), modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Locations", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondaryLabel)
                Spacer(Modifier.height(8.dp))
                viewModel.homeAnchor?.let { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Home, null, tint = c.green, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Home", fontSize = 15.sp, color = c.label) }; Spacer(Modifier.height(6.dp)) }
                viewModel.storeAnchor?.let { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Store, null, tint = c.orange, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Store / Hub", fontSize = 15.sp, color = c.label) }; Spacer(Modifier.height(6.dp)) }
                viewModel.collegeAnchor?.let { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.School, null, tint = c.blue, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("College", fontSize = 15.sp, color = c.label) } }
            }
        }
    }
}
