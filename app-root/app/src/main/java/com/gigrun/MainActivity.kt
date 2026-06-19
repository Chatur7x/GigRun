package com.gigrun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gigrun.presentation.crash.CrashCountdownOverlay
import com.gigrun.presentation.dashboard.DashboardScreen
import com.gigrun.presentation.maintenance.MaintenanceScreen
import com.gigrun.presentation.map.MapScreen
import com.gigrun.presentation.platforms.PlatformCompareScreen
import com.gigrun.presentation.settings.SettingsScreen
import com.gigrun.presentation.trips.TripDetailScreen
import com.gigrun.presentation.trips.TripListScreen
import com.gigrun.presentation.trips.TripsViewModel
import com.gigrun.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home)
    data object Platforms : Screen("platforms", "Compare", Icons.Filled.Leaderboard)
    data object Trips : Screen("trips", "Trips", Icons.Filled.TwoWheeler)
    data object TripDetail : Screen("trip_detail", "Detail", Icons.Filled.Info)
    data object Maintenance : Screen("maintenance", "Vehicle", Icons.Filled.Build)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Map : Screen("map", "Map", Icons.Filled.Map)
}

val bottomNavItems = listOf(Screen.Dashboard, Screen.Map, Screen.Trips, Screen.Platforms, Screen.Maintenance, Screen.Settings)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            // Step 1: Request foreground location + SMS + notifications
            val foregroundPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            // Step 2: Background location launcher (Android 10+)
            val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted or denied */ }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SEND_SMS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                foregroundPermissionLauncher.launch(perms.toTypedArray())
            }

            // Request background location after a short delay to ensure foreground is granted
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val hasBg = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasBg) {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }

            GigRunTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    containerColor = DeepCarbon,
                    bottomBar = {
                        if (currentRoute != Screen.TripDetail.route) {
                            NavigationBar(containerColor = DarkSurface, tonalElevation = 0.dp) {
                                bottomNavItems.forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title, maxLines = 1) },
                                        selected = currentRoute == screen.route,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = CyberCyan,
                                            selectedTextColor = CyberCyan,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary,
                                            indicatorColor = CyberCyan.copy(alpha = 0.12f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding).background(DeepCarbon)) {
                        // Share TripsViewModel across trip list and detail screens
                        val tripsViewModel: TripsViewModel = hiltViewModel()

                        NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
                            composable(Screen.Dashboard.route) { DashboardScreen() }
                            composable(Screen.Platforms.route) { PlatformCompareScreen() }
                            composable(Screen.Trips.route) {
                                TripListScreen(viewModel = tripsViewModel, onTripClick = { navController.navigate(Screen.TripDetail.route) })
                            }
                            composable(Screen.TripDetail.route) {
                                TripDetailScreen(viewModel = tripsViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(Screen.Map.route) { MapScreen() }
                            composable(Screen.Maintenance.route) { MaintenanceScreen() }
                            composable(Screen.Settings.route) { SettingsScreen() }
                        }
                        CrashCountdownOverlay()
                    }
                }
            }
        }
    }
}
