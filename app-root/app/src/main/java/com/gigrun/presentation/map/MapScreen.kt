package com.gigrun.presentation.map

import android.Manifest
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)

    var homeAnchor by mutableStateOf<LatLng?>(null)
    var storeAnchor by mutableStateOf<LatLng?>(null)
    var collegeAnchor by mutableStateOf<LatLng?>(null)
    var isLoaded by mutableStateOf(false)

    init {
        loadAnchors()
    }

    private fun loadAnchors() {
        viewModelScope.launch {
            val home = prefs.homeAnchor.first()
            if (home != null) homeAnchor = LatLng(home.first, home.second)

            val store = prefs.storeAnchor.first()
            if (store != null) storeAnchor = LatLng(store.first, store.second)

            val college = prefs.collegeAnchor.first()
            if (college != null) collegeAnchor = LatLng(college.first, college.second)

            isLoaded = true
        }
    }
}

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current

    if (!viewModel.isLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(DeepCarbon), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CyberCyan)
                Spacer(Modifier.height(12.dp))
                Text("Loading map...", color = TextSecondary, fontSize = 14.sp)
            }
        }
        return
    }

    // Check if any anchors are set
    val hasAnchors = viewModel.homeAnchor != null || viewModel.storeAnchor != null || viewModel.collegeAnchor != null

    if (!hasAnchors) {
        // Show a friendly empty state when no locations are set
        Box(modifier = Modifier.fillMaxSize().background(DeepCarbon), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Map, null, tint = CyberCyan, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Locations Set",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Go to Settings and add your Home, Store/Hub, and College coordinates to see them on the map.",
                        fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val defaultCenter = viewModel.homeAnchor ?: viewModel.storeAnchor ?: viewModel.collegeAnchor ?: LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 14f)
    }

    // Check location permission at runtime
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val mapProperties = MapProperties(
        isMyLocationEnabled = hasLocationPermission,
        mapType = MapType.NORMAL
    )
    val mapUiSettings = MapUiSettings(
        myLocationButtonEnabled = hasLocationPermission,
        mapToolbarEnabled = true,
        zoomControlsEnabled = true,
        compassEnabled = true
    )

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            viewModel.homeAnchor?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Home",
                    snippet = "Your Base"
                )
            }
            viewModel.storeAnchor?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Store / Hub",
                    snippet = "Pickup Point"
                )
            }
            viewModel.collegeAnchor?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "College",
                    snippet = "Campus"
                )
            }
        }

        // Map legend overlay at top
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("LOCATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                viewModel.homeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home", fontSize = 12.sp, color = TextPrimary)
                    }
                }
                viewModel.storeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Store, null, tint = MoltenAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Store / Hub", fontSize = 12.sp, color = TextPrimary)
                    }
                }
                viewModel.collegeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("College", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }
    }
}
