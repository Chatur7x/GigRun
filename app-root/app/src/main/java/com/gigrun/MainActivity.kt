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
import androidx.compose.ui.unit.sp
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
    data object Map : Screen("map", "Map", Icons.Filled.Map)
    data object Trips : Screen("trips", "Trips", Icons.Filled.TwoWheeler)
    data object TripDetail : Screen("trip_detail", "Detail", Icons.Filled.Info)
    data object Platforms : Screen("platforms", "Compare", Icons.Filled.Leaderboard)
    data object Maintenance : Screen("maintenance", "Vehicle", Icons.Filled.Build)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(Screen.Dashboard, Screen.Map, Screen.Trips, Screen.Platforms, Screen.Maintenance, Screen.Settings)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            val foregroundPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }

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

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }

            GigRunTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    containerColor = SystemBackground,
                    bottomBar = {
                        if (currentRoute != Screen.TripDetail.route) {
                            // Apple iOS-style tab bar
                            NavigationBar(
                                containerColor = SecondaryBackground,
                                tonalElevation = 0.dp
                            ) {
                                bottomNavItems.forEach { screen ->
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                screen.icon,
                                                contentDescription = screen.title,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                screen.title,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                letterSpacing = 0.sp
                                            )
                                        },
                                        selected = currentRoute == screen.route,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = SystemBlue,
                                            selectedTextColor = SystemBlue,
                                            unselectedIconColor = SystemGray,
                                            unselectedTextColor = SystemGray,
                                            indicatorColor = SystemBlue.copy(alpha = 0.0f) // No indicator — Apple style
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding).background(SystemBackground)) {
                        val tripsViewModel: TripsViewModel = hiltViewModel()

                        NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
                            composable(Screen.Dashboard.route) { DashboardScreen() }
                            composable(Screen.Map.route) { MapScreen() }
                            composable(Screen.Trips.route) {
                                TripListScreen(viewModel = tripsViewModel, onTripClick = { navController.navigate(Screen.TripDetail.route) })
                            }
                            composable(Screen.TripDetail.route) {
                                TripDetailScreen(viewModel = tripsViewModel, onBack = { navController.popBackStack() })
                            }
                            composable(Screen.Platforms.route) { PlatformCompareScreen() }
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
