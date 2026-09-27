package com.gigrun.presentation.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import com.gigrun.ui.components.EmptyState
import com.gigrun.ui.components.GigCard
import com.gigrun.ui.design.LocalGigRunColors
import com.gigrun.ui.theme.AppleMapStyle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val prefs: UserPreferences,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    var homeAnchor by mutableStateOf<LatLng?>(null)
    var storeAnchor by mutableStateOf<LatLng?>(null)
    var collegeAnchor by mutableStateOf<LatLng?>(null)
    var isLoaded by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    var mapsKeyMissing by mutableStateOf(false)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loadError = null
            try {
                // Fail-visible, not fail-silent: fresh clones ship an empty Maps key
                // (build.gradle falls back to "") — say so instead of grey tiles.
                mapsKeyMissing = try {
                    val ai = appContext.packageManager.getApplicationInfo(
                        appContext.packageName,
                        android.content.pm.PackageManager.GET_META_DATA
                    )
                    (ai.metaData?.getString("com.google.android.geo.API_KEY") ?: "").isBlank()
                } catch (_: Exception) { false }
                prefs.homeAnchor.first()?.let { homeAnchor = LatLng(it.first, it.second) }
                prefs.storeAnchor.first()?.let { storeAnchor = LatLng(it.first, it.second) }
                prefs.collegeAnchor.first()?.let { collegeAnchor = LatLng(it.first, it.second) }
                isLoaded = true
            } catch (e: Exception) {
                android.util.Log.e("MapVM", "anchor load failed", e)
                loadError = "Couldn't load saved places."
            }
        }
    }
}

@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {}
) {
    val c = LocalGigRunColors.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val context = LocalContext.current

    if (!viewModel.isLoaded) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            if (viewModel.loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(viewModel.loadError ?: "", fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.load() }, shape = RoundedCornerShape(12.dp)) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
        }
        return
    }

    val hasAnchors = viewModel.homeAnchor != null || viewModel.storeAnchor != null || viewModel.collegeAnchor != null
    if (viewModel.mapsKeyMissing) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            GigCard(
                modifier = Modifier.padding(32.dp),
                entranceIndex = -1,
                entranceVisible = entered
            ) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.KeyOff, null, tint = c.warning, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Maps unavailable", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("No Google Maps API key is configured in this build. Trips still track — only the map view is affected.", fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.load() }, shape = RoundedCornerShape(12.dp)) {
                        Text("Retry")
                    }
                }
            }
        }
        return
    }
    if (!hasAnchors) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            GigCard(
                modifier = Modifier.padding(32.dp),
                entranceIndex = -1,
                entranceVisible = entered
            ) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Map, null, tint = c.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Locations", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("Add coordinates in Settings.", fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSettingsClick, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Settings")
                    }
                }
            }
        }
        return
    }

    val defaultCenter = viewModel.homeAnchor ?: viewModel.storeAnchor ?: viewModel.collegeAnchor ?: LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultCenter, 14f) }
    // Fix: key permission check on lifecycle resume so granting location in Settings
    // refreshes My-Location without an app restart.
    var locCheckTick by remember { mutableStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) locCheckTick++
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val hasLoc = remember(locCheckTick) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }

    Box(Modifier.fillMaxSize()) {
        // Render Google Maps using Apple Maps Custom JSON Theme
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLoc,
                mapStyleOptions = MapStyleOptions(AppleMapStyle.json)
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasLoc,
                zoomControlsEnabled = false,
                compassEnabled = true
            )
        ) {
            viewModel.homeAnchor?.let { Marker(MarkerState(it), title = "Home") }
            viewModel.storeAnchor?.let { Marker(MarkerState(it), title = "Store / Hub") }
            viewModel.collegeAnchor?.let { Marker(MarkerState(it), title = "College") }
        }

        // Location-off banner: visible guidance instead of silently disabled My-Location.
        if (!hasLoc) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = c.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.LocationOff, null, tint = c.warning, modifier = Modifier.size(18.dp))
                    Text("Location off — live position disabled", fontSize = 12.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSettingsClick, contentPadding = PaddingValues(0.dp)) {
                        Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                }
            }
        }

        // Horizontal bottom legend overlay
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface.copy(alpha = 0.94f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Anchors:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
                
                viewModel.homeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, null, tint = c.success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                viewModel.storeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Store, null, tint = c.warning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Store / Hub", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                viewModel.collegeAnchor?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, null, tint = c.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("College", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
