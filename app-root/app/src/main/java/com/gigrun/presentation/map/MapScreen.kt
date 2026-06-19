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
import com.gigrun.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {
    var homeAnchor by mutableStateOf<LatLng?>(null)
    var storeAnchor by mutableStateOf<LatLng?>(null)
    var collegeAnchor by mutableStateOf<LatLng?>(null)
    var isLoaded by mutableStateOf(false)

    init {
        viewModelScope.launch {
            prefs.homeAnchor.first()?.let { homeAnchor = LatLng(it.first, it.second) }
            prefs.storeAnchor.first()?.let { storeAnchor = LatLng(it.first, it.second) }
            prefs.collegeAnchor.first()?.let { collegeAnchor = LatLng(it.first, it.second) }
            isLoaded = true
        }
    }
}

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current

    if (!viewModel.isLoaded) {
        Box(Modifier.fillMaxSize().background(SystemBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SystemBlue, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
        return
    }

    val hasAnchors = viewModel.homeAnchor != null || viewModel.storeAnchor != null || viewModel.collegeAnchor != null

    if (!hasAnchors) {
        Box(Modifier.fillMaxSize().background(SystemBackground), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = SecondaryBackground, modifier = Modifier.padding(32.dp)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Map, null, tint = SystemBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Locations", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LabelPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("Add your Home, Store, and College coordinates in Settings.", fontSize = 15.sp, color = LabelSecondary, textAlign = TextAlign.Center, letterSpacing = (-0.24).sp)
                }
            }
        }
        return
    }

    val defaultCenter = viewModel.homeAnchor ?: viewModel.storeAnchor ?: viewModel.collegeAnchor ?: LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 14f)
    }
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission, mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission, mapToolbarEnabled = true, zoomControlsEnabled = false, compassEnabled = true)
        ) {
            viewModel.homeAnchor?.let { Marker(state = MarkerState(position = it), title = "Home", snippet = "Your base") }
            viewModel.storeAnchor?.let { Marker(state = MarkerState(position = it), title = "Store / Hub", snippet = "Pickup point") }
            viewModel.collegeAnchor?.let { Marker(state = MarkerState(position = it), title = "College", snippet = "Campus") }
        }

        // Apple-style floating legend card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SecondaryBackground.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Locations", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LabelSecondary, letterSpacing = (-0.08).sp)
                Spacer(Modifier.height(8.dp))
                viewModel.homeAnchor?.let {
                    LegendRow(Icons.Filled.Home, "Home", SystemGreen)
                    Spacer(Modifier.height(6.dp))
                }
                viewModel.storeAnchor?.let {
                    LegendRow(Icons.Filled.Store, "Store / Hub", SystemOrange)
                    Spacer(Modifier.height(6.dp))
                }
                viewModel.collegeAnchor?.let {
                    LegendRow(Icons.Filled.School, "College", SystemBlue)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, color = LabelPrimary, letterSpacing = (-0.24).sp)
    }
}
