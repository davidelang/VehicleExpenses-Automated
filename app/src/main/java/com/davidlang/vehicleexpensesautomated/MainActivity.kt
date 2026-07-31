package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import android.content.SharedPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import com.davidlang.vehicleexpensesautomated.data.batch.BatchImportPendingStore
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdBackfill
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.about.AboutScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryMode
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentAlignmentScreen
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentPumpScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.help.HelpScreen
import com.davidlang.vehicleexpensesautomated.ui.import.ImportOldPicturesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.ReportsScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelEditScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelHistoryScreen
import com.davidlang.vehicleexpensesautomated.ui.trip.TripTrackingScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabCostTrendsScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabEfficiencyScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabExpenseCategoriesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabFillHistoryScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabHubScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabMonthlyCostsScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabTripMilesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabVehicleSummaryScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.PhotoBackupScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SpreadsheetSyncScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SyncingScreen
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.ManageVehiclesScreen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vehicleRepository: VehicleRepository

    @Inject
    lateinit var syncIdBackfill: SyncIdBackfill

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var photoBackupManager: PhotoBackupManager

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Photos permission denied. Fuel photo saving to Camera roll may fail until Photos access is granted in system Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
        // After media dialog settles → location (no stacked dialogs).
        maybeRequestLocationPermission()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val anyGranted = results.values.any { it }
        if (!anyGranted) {
            Toast.makeText(
                this,
                "Location denied — fills save without GPS",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission denied. Photo features will be disabled.", Toast.LENGTH_LONG).show()
        }
        // D6: request media only after camera dialog settles (granted or denied).
        maybeRequestMediaPermissionForFuelPhotos()
    }

    private fun mediaImagesPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun maybeRequestMediaPermissionForFuelPhotos() {
        val prefs = getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        val saveFuelPhotos = prefs.getBoolean("save_fuel_photos", true)
        if (!saveFuelPhotos) {
            maybeRequestLocationPermission()
            return
        }
        val permission = mediaImagesPermission()
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            maybeRequestLocationPermission()
            return
        }
        mediaPermissionLauncher.launch(permission)
    }

    /** One-shot FINE+COARSE after camera/media chain; soft deny toast only. */
    private fun maybeRequestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) return
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Camera only here — media then location follow in callbacks (no stacked dialogs).
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            VehicleExpensesAutomatedTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                var backfillComplete by remember { mutableStateOf(syncIdBackfill.isBackfillDone()) }
                var syncFailureVisible by remember { mutableStateOf(false) }
                var pendingReviewCount by remember { mutableStateOf(0) }

                fun refreshChromeIndicators() {
                    syncFailureVisible = SyncFailureStore(context).hasAnyFailure()
                    pendingReviewCount = BatchImportPendingStore.count(context)
                }

                LaunchedEffect(backfillComplete, navController.currentBackStackEntry) {
                    refreshChromeIndicators()
                }

                // Refresh yellow ? when app returns to foreground (after merge/answer/sync)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshChromeIndicators()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (!backfillComplete) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Updating database after upgrade…",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "This usually takes a few seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        try {
                            withContext(Dispatchers.IO) {
                                syncIdBackfill.runIfNeeded()
                                vehicleRepository.ensureUnassignedVehicle()
                                syncManager.scheduleFromDestination()
                                photoBackupManager.scheduleFromDestination()
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "sync-id backfill failed", e)
                            Toast.makeText(
                                context,
                                "Database upgrade failed — restart the app",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        backfillComplete = true
                    }
                    return@VehicleExpensesAutomatedTheme
                }

                val experimentPrefs = remember {
                    context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
                }
                var showExperimentScreens by remember {
                    mutableStateOf(experimentPrefs.getBoolean("show_experiment_screens", false))
                }
                DisposableEffect(experimentPrefs) {
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        if (key == "show_experiment_screens") {
                            showExperimentScreens = prefs.getBoolean("show_experiment_screens", false)
                        }
                    }
                    experimentPrefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { experimentPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                // Dynamic page title
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val title = when {
                    currentRoute == "quickfill" -> "Quick Fill-up"
                    currentRoute == "triptracking" -> "Trip Tracking"
                    currentRoute == "managevehicles" -> "Manage Vehicles"
                    currentRoute == "expense" -> "New Expense Entry"
                    currentRoute?.startsWith("expense/") == true -> "Edit Expense"
                    currentRoute == "expenselist" -> "Expense List"
                    currentRoute == "import" ||
                        currentRoute?.startsWith("import") == true -> "Import Old Pictures"
                    currentRoute == "reports" -> "Reports & Charts"
                    currentRoute == "reports_lab" ||
                        currentRoute?.startsWith("reports_lab/") == true -> "Reports Lab"
                    currentRoute == "fuelhistory" -> "Fuel History"
                    currentRoute?.startsWith("fuel/") == true -> "Edit Fill"
                    currentRoute == "settings" -> "Settings"
                    currentRoute == "syncing" -> "Syncing"
                    currentRoute == "settings/spreadsheet_sync" -> "Spreadsheet Sync"
                    currentRoute == "settings/photo_backup" -> "Photo Backup"
                    currentRoute == "help" -> "Help"
                    currentRoute == "about" -> "About"
                    currentRoute == "experiment" -> "Alignment Experiment"
                    currentRoute == "experiment_pump" -> "Gas Pump Extraction Experiment"
                    else -> "Vehicle Expenses"
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Vehicle Expenses", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            NavigationDrawerItem(
                                label = { Text("Quick Fill-up") },
                                selected = false,
                                onClick = {
                                    navController.navigate("quickfill")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Trip Tracking") },
                                selected = false,
                                onClick = {
                                    navController.navigate("triptracking")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Manage Vehicles") },
                                selected = false,
                                onClick = {
                                    navController.navigate("managevehicles")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("New Expense Entry") },
                                selected = false,
                                onClick = {
                                    navController.navigate("expense")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Expense List") },
                                selected = false,
                                onClick = {
                                    navController.navigate("expenselist")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Import Old Pictures") },
                                selected = false,
                                onClick = {
                                    navController.navigate("import")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Reports & Charts") },
                                selected = false,
                                onClick = {
                                    navController.navigate("reports")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Reports Lab") },
                                selected = false,
                                onClick = {
                                    navController.navigate("reports_lab")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Fuel History") },
                                selected = false,
                                onClick = {
                                    navController.navigate("fuelhistory")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Settings") },
                                selected = false,
                                onClick = {
                                    navController.navigate("settings")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Syncing") },
                                selected = false,
                                onClick = {
                                    navController.navigate("syncing")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Help") },
                                selected = false,
                                onClick = {
                                    navController.navigate("help")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("About") },
                                selected = false,
                                onClick = {
                                    navController.navigate("about")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            if (showExperimentScreens) {
                                NavigationDrawerItem(
                                    label = { Text("Alignment Experiment") },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                NavigationDrawerItem(
                                    label = { Text("Pump Experiment") },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment_pump")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        if (title == "Vehicle Expenses") title else "Vehicle Expenses - $title",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = true,
                                    )
                                },
                                actions = {
                                    // Order: review questions (yellow), then sync failure (red)
                                    if (pendingReviewCount > 0) {
                                        IconButton(
                                            onClick = {
                                                navController.navigate("import?review=1") {
                                                    launchSingleTop = true
                                                }
                                            },
                                            modifier = Modifier
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .semantics {
                                                    contentDescription = "Review questions"
                                                },
                                        ) {
                                            Text(
                                                if (pendingReviewCount > 99) "?99+"
                                                else "?$pendingReviewCount",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (syncFailureVisible) {
                                        IconButton(
                                            onClick = { navController.navigate("syncing") },
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                        ) {
                                            Text(
                                                "!",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    val isSettingsSubRoute = currentRoute == "settings/spreadsheet_sync" ||
                                        currentRoute == "settings/photo_backup" ||
                                        currentRoute?.startsWith("fuel/") == true ||
                                        currentRoute?.startsWith("reports_lab/") == true
                                    if (isSettingsSubRoute) {
                                        IconButton(
                                            onClick = { navController.popBackStack() },
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                        ) {
                                            Text("←")
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                        ) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        Surface(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = "quickfill",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("quickfill") { QuickFillupScreen(navController = navController) }
                                composable("managevehicles") { ManageVehiclesScreen(navController = navController) }
                                composable("expense") {
                                    ExpenseEntryScreen(
                                        navController = navController,
                                        mode = ExpenseEntryMode.Create,
                                    )
                                }
                                composable("expense/{expenseId}") { backStackEntry ->
                                    val id = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull()
                                    ExpenseEntryScreen(
                                        navController = navController,
                                        mode = ExpenseEntryMode.fromRoute(id),
                                    )
                                }
                                composable("expenselist") { ExpenseListScreen(navController = navController) }
                                composable(
                                    route = "import?review={review}",
                                    arguments = listOf(
                                        navArgument("review") {
                                            type = NavType.StringType
                                            defaultValue = "0"
                                        },
                                    ),
                                ) { entry ->
                                    val expandReview =
                                        entry.arguments?.getString("review") == "1"
                                    ImportOldPicturesScreen(
                                        navController = navController,
                                        expandReview = expandReview,
                                    )
                                }
                                composable("reports") { ReportsScreen(navController = navController) }
                                composable("triptracking") { TripTrackingScreen(navController = navController) }
                                composable("reports_lab") { ReportsLabHubScreen(navController = navController) }
                                composable("reports_lab/efficiency") {
                                    ReportsLabEfficiencyScreen(navController = navController)
                                }
                                composable("reports_lab/cost_trends") {
                                    ReportsLabCostTrendsScreen(navController = navController)
                                }
                                composable("reports_lab/monthly") {
                                    ReportsLabMonthlyCostsScreen(navController = navController)
                                }
                                composable("reports_lab/expenses") {
                                    ReportsLabExpenseCategoriesScreen(navController = navController)
                                }
                                composable("reports_lab/fills") {
                                    ReportsLabFillHistoryScreen(navController = navController)
                                }
                                composable("reports_lab/vehicle_summary") {
                                    ReportsLabVehicleSummaryScreen(navController = navController)
                                }
                                composable("reports_lab/trips") {
                                    ReportsLabTripMilesScreen(navController = navController)
                                }
                                composable("fuelhistory") { FuelHistoryScreen(navController = navController) }
                                composable("fuel/{fuelId}") { backStackEntry ->
                                    val id = backStackEntry.arguments?.getString("fuelId")?.toLongOrNull()
                                    if (id != null) {
                                        FuelEditScreen(navController = navController, fuelId = id)
                                    }
                                }
                                composable("settings") { SettingsScreen(navController = navController) }
                                composable("syncing") { SyncingScreen(navController = navController) }
                                composable("settings/spreadsheet_sync") {
                                    SpreadsheetSyncScreen(navController = navController)
                                }
                                composable("settings/photo_backup") {
                                    PhotoBackupScreen(navController = navController)
                                }
                                composable("help") { HelpScreen() }
                                composable("about") { AboutScreen() }
                                composable("experiment") { ExperimentAlignmentScreen(navController = navController) }
                                composable("experiment_pump") { ExperimentPumpScreen(navController = navController) }
                            }
                        }
                    }
                }
            }
        }
    }
}
