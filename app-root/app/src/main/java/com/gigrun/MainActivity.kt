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
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.gigrun.data.preferences.UserPreferences
import com.gigrun.presentation.comparison.PlatformComparisonScreen
import com.gigrun.presentation.crash.CrashCountdownOverlay
import com.gigrun.presentation.dashboard.DashboardScreen
import com.gigrun.presentation.expenses.ExpensesScreen
import com.gigrun.presentation.fuelbike.FuelBikeScreen
import com.gigrun.presentation.goals.GoalsScreen
import com.gigrun.presentation.maintenance.MaintenanceScreen
import com.gigrun.presentation.map.MapScreen
import com.gigrun.presentation.platforms.PlatformCompareScreen
import com.gigrun.presentation.settings.SettingsScreen
import com.gigrun.presentation.penalties.PenaltyTrackerScreen
import com.gigrun.presentation.tax.TaxHelperScreen
import com.gigrun.presentation.trips.TripDetailScreen
import com.gigrun.presentation.trips.TripListScreen
import com.gigrun.presentation.trips.TripsViewModel
import com.gigrun.ui.design.GigRunTheme
import com.gigrun.ui.design.LocalGigRunColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home)
    data object Map : Screen("map", "Map", Icons.Filled.Map)
    data object Trips : Screen("trips", "Trips", Icons.Filled.TwoWheeler)
    data object TripDetail : Screen("trip_detail", "Detail", Icons.Filled.Info) {
        fun routeFor(tripId: Long) = "trip_detail/$tripId"
    }
    data object Platforms : Screen("platforms", "Compare", Icons.Filled.Leaderboard)
    data object Maintenance : Screen("maintenance", "Vehicle", Icons.Filled.Build)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    // Extra screens (not in bottom bar)
    data object Expenses : Screen("expenses", "Expenses", Icons.Filled.ReceiptLong)
    data object Goals : Screen("goals", "Goals", Icons.Filled.Flag)
    data object Tax : Screen("tax", "Tax", Icons.Filled.AccountBalance)
    data object FuelBike : Screen("fuelbike", "Fuel & Bike", Icons.Filled.LocalGasStation)
    data object Penalties : Screen("penalties", "Penalties", Icons.Filled.Warning)
    // Feature 28 — read-only platform economics. Distinct from Screen.Platforms,
    // which is the legacy gross-trips "Compare" tab.
    // TODO(v14, Feature 28): consolidation deferred — see docs/known-issues.md.
    data object Comparison : Screen("comparison", "Comparison", Icons.Filled.BarChart)
}

val bottomNavItems = listOf(Screen.Dashboard, Screen.Map, Screen.Trips, Screen.Platforms, Screen.Maintenance, Screen.Settings)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // R&D fix: use Hilt singleton instead of manual UserPreferences(context) —
    // manual construction created a second DataStore owner for the same file.
    @Inject lateinit var prefsSingleton: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { prefsSingleton }
            var themeMode by remember { mutableStateOf("system") }
            LaunchedEffect(Unit) { themeMode = prefs.themeMode.first() }
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            GigRunTheme(darkTheme = isDark) {
                val c = LocalGigRunColors.current
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in bottomNavItems.map { it.route }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    val denied = grants.filterValues { !it }.keys
                    if (denied.isNotEmpty()) {
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                "Location denied — tracking won't work.",
                                actionLabel = "Settings",
                                duration = SnackbarDuration.Long
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", context.packageName, null)
                                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (!granted) {
                        scope.launch { snackbarHostState.showSnackbar("Background location denied — trips pause when app is closed.") }
                    }
                }

                LaunchedEffect(Unit) {
                    // Location first; SEND_SMS is requested contextually when crash
                    // detection is enabled (Settings) — never in the cold-start batch.
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    fgLauncher.launch(perms.toTypedArray())
                }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    // Only ask for background after foreground location is granted —
                    // otherwise the double-prompt tanks grant rate for no benefit.
                    val fgGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fgGranted &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = c.background,
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                                bottomNavItems.forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, screen.title, Modifier.size(22.dp)) },
                                        label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                                        selected = currentRoute == screen.route,
                                        onClick = { navController.navigate(screen.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = c.primary,
                                            selectedTextColor = c.primary,
                                            unselectedIconColor = c.textTertiary,
                                            unselectedTextColor = c.textTertiary,
                                            indicatorColor = c.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        val tripsVm: TripsViewModel = hiltViewModel()
                        NavHost(navController, Screen.Dashboard.route) {
                            composable(Screen.Dashboard.route) {
                                DashboardScreen(
                                    onSettingsClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                                    onNavigateToPenalties = { navController.navigate(Screen.Penalties.route) { launchSingleTop = true } },
                                    onNavigateToComparison = { navController.navigate(Screen.Comparison.route) { launchSingleTop = true } }
                                )
                            }
                            composable(Screen.Map.route) { MapScreen(onSettingsClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }) }
                            composable(Screen.Trips.route) { TripListScreen(tripsVm) { navController.navigate(Screen.TripDetail.routeFor(it.id)) { launchSingleTop = true } } }
                            composable(
                                route = "trip_detail/{tripId}",
                                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.LongType })
                            ) { backStackEntry ->
                                val tripId = backStackEntry.arguments?.getLong("tripId")
                                TripDetailScreen(tripsVm, tripId = tripId) { navController.popBackStack() }
                            }
                            composable(Screen.Platforms.route) { PlatformCompareScreen() }
                            // Vehicle tab: full Fuel & Bike beast (fuel + GigChain + health).
                            // MaintenanceScreen reachable via Settings? No — kept as legacy dead
                            // code guard: render it nowhere would rot; instead Vehicle tab hosts
                            // FuelBike, and Maintenance stays importable for tests.
                            composable(Screen.Maintenance.route) {
                                MaintenanceScreen(
                                    onFuelBikeClick = { navController.navigate(Screen.FuelBike.route) { launchSingleTop = true } },
                                    onSettingsClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
                                )
                            }
                            composable(Screen.FuelBike.route) { FuelBikeScreen(onBackClick = { navController.popBackStack() }) }
                            composable(Screen.Penalties.route) { PenaltyTrackerScreen(onBack = { navController.popBackStack() }) }
                            composable(Screen.Comparison.route) { PlatformComparisonScreen(onBack = { navController.popBackStack() }) }
                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    // Settings is also a bottom-bar tab: popping the last
                                    // entry would exit the app — go Home instead.
                                    onBackClick = {
                                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                                        else navController.navigate(Screen.Dashboard.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    },
                                    onNavigateExpenses = { navController.navigate(Screen.Expenses.route) { launchSingleTop = true } },
                                    onNavigateGoals = { navController.navigate(Screen.Goals.route) { launchSingleTop = true } },
                                    onNavigateTax = { navController.navigate(Screen.Tax.route) { launchSingleTop = true } },
                                    onThemeChange = { themeMode = it }
                                )
                            }
                            composable(Screen.Expenses.route) { ExpensesScreen(onBackClick = { navController.popBackStack() }) }
                            composable(Screen.Goals.route) { GoalsScreen(onBack = { navController.popBackStack() }) }
                            composable(Screen.Tax.route) {
                                TaxHelperScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onAddExpenseClick = { navController.navigate(Screen.Expenses.route) { launchSingleTop = true } }
                                )
                            }
                        }
                        CrashCountdownOverlay()
                    }
                }
            }
        }
    }
}
