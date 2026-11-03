package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import android.content.SharedPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.DbState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import com.davidlang.vehicleexpensesautomated.ui.db.DatabaseErrorActivity
import com.davidlang.vehicleexpensesautomated.ui.startup.StartupFailureActivity
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.data.repository.forUserPicker
import com.davidlang.vehicleexpensesautomated.ui.onboarding.OnboardingSplashScreen
import com.davidlang.vehicleexpensesautomated.ui.onboarding.TutorialPagerScreen
import com.davidlang.vehicleexpensesautomated.data.sync.PhotoBackupManager
import com.davidlang.vehicleexpensesautomated.data.sync.SyncFailureStore
import com.davidlang.vehicleexpensesautomated.data.sync.SyncIdBackfill
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.about.AboutScreen
import com.davidlang.vehicleexpensesautomated.ui.components.LocalPageHelpController
import com.davidlang.vehicleexpensesautomated.ui.components.PageHelpTopBarAction
import com.davidlang.vehicleexpensesautomated.ui.components.rememberPageHelpController
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryMode
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FillScreen
import com.davidlang.vehicleexpensesautomated.ui.help.HelpScreen

import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelEditScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelHistoryScreen
import com.davidlang.vehicleexpensesautomated.ui.trip.TripTrackingScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabExpenseCategoriesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabFillHistoryScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabHubScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabTimeChartsScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabTripMilesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.lab.ReportsLabVehicleSummaryScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.PhotoBackupScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SpreadsheetSyncScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SyncingScreen
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.OcrHarness
import com.davidlang.vehicleexpensesautomated.ui.util.OdometerOcrUtils
import com.davidlang.vehicleexpensesautomated.ui.vehicle.ManageVehicles2Screen
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vehicleRepository: VehicleRepository

    @Inject
    lateinit var dbState: DatabaseHealthState

    @Inject
    lateinit var syncIdBackfill: SyncIdBackfill

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var photoBackupManager: PhotoBackupManager

    @Inject
    lateinit var emailReceiptManager: com.davidlang.vehicleexpensesautomated.data.email.EmailReceiptManager

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                getString(R.string.nav_photos_permission_denied_fuel_photo_saving),
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
                getString(R.string.nav_location_denied_fills_save_without_gps),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.nav_camera_permission_denied_photo_features_wi), Toast.LENGTH_LONG).show()
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

    /** [Failed] leaves this task. [Unchecked] is verified first. */
    private fun leaveForDatabaseError(): Boolean {
        var health = dbState.states.value
        if (health is Unchecked) {
            (application as VehicleExpensesApplication).verifyDatabase()
            health = dbState.states.value
        }
        if (health is Failed) {
            startDatabaseError()
            return true
        }
        return false
    }

    private fun startDatabaseError() {
        startActivity(
            Intent(this, DatabaseErrorActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }

    private fun leaveForStartupFailure(): Boolean {
        val failure = (application as VehicleExpensesApplication).startupFailure
        if (failure == null) return false
        startActivity(
            Intent(this, StartupFailureActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
        return true
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-composition reads intent via setContent; force recreate for deep-link re-entry
    }

    override fun attachBaseContext(newBase: Context) {
        // setApplicationLocales does not reach a ComponentActivity before API 33.
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (leaveForStartupFailure()) return
        if (leaveForDatabaseError()) return

        // Camera only here — media then location follow in callbacks (no stacked dialogs).
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            val health by dbState.state.collectAsState()
            val transferProgress by dbState.progress.collectAsState()
            when (health) {
                is Failed -> {
                    LaunchedEffect(Unit) { startDatabaseError() }
                }
                is Exporting, is Importing -> TransferBlockingScreen(health, transferProgress)
                Unchecked -> {
                    LaunchedEffect(Unit) {
                        (application as VehicleExpensesApplication).verifyDatabase()
                    }
                }
                Ok -> {
            ExportFailedNotice(dbState)
            VehicleExpensesAutomatedTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                var backfillComplete by remember { mutableStateOf(syncIdBackfill.isBackfillDone()) }
                var syncFailureVisible by remember { mutableStateOf(false) }
                var pendingReviewCount by remember { mutableStateOf(0) }
                val experimentsAvailable = false
                val experimentPrefs = remember {
                    context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
                }
                var storedShowExperimentScreens by remember {
                    mutableStateOf(experimentPrefs.getBoolean("show_experiment_screens", false))
                }
                DisposableEffect(experimentPrefs) {
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        if (key == "show_experiment_screens") {
                            storedShowExperimentScreens =
                                prefs.getBoolean("show_experiment_screens", false)
                        }
                    }
                    experimentPrefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { experimentPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }
                val experimentsEnabled = experimentsAvailable && storedShowExperimentScreens

                fun refreshChromeIndicators() {
                    syncFailureVisible = SyncFailureStore(context).hasAnyFailure()
                    pendingReviewCount = BatchImportPendingStore.count(context)
                }

                LaunchedEffect(backfillComplete, navController.currentBackStackEntry) {
                    refreshChromeIndicators()
                }

                // Explicit deep-link routing for experiment automation (qa APP_SCHEME only):
                // adb shell am start -a android.intent.action.VIEW \
                //   -d 'vehicleexpensestest://experiment/align?auto=first10'
                val deepLinkKey = intent?.dataString
                LaunchedEffect(backfillComplete, deepLinkKey, experimentsEnabled) {
                    if (!backfillComplete || !experimentsEnabled) return@LaunchedEffect
                    val data = intent?.data ?: return@LaunchedEffect
                    if (data.scheme != BuildConfig.APP_SCHEME || data.host != "experiment") return@LaunchedEffect
                    val path = data.path.orEmpty()
                    val auto = data.getQueryParameter("auto").orEmpty()
                    val q = if (auto.isNotEmpty()) "?auto=$auto" else ""
                    val route = when {
                        path.contains("align") -> "experiment$q"
                        path.contains("multiscale") || path.contains("multi_scale") ->
                            "experiment_multiscale_det$q"
                        path.contains("pump") -> "experiment_pump$q"
                        path.contains("heatmap") || path.contains("preprocess") ||
                            path.contains("odo-export") || path.contains("deskew") ->
                            "experiment_heatmap_stage$q"
                        else -> return@LaunchedEffect
                    }
                    // Let NavHost finish composing before navigating.
                    delay(400)
                    Log.i("MainActivity", "Deep link navigate → $route from $data")
                    try {
                        navController.navigate(route) {
                            launchSingleTop = true
                            // Drop any restored settings stack so experiment is visible.
                            popUpTo("fill") { inclusive = false }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Deep link navigate failed for $route", e)
                    }
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
                                getString(R.string.nav_updating_database_after_upgrade),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                getString(R.string.nav_this_usually_takes_a_few_seconds),
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
                                emailReceiptManager.rescheduleFromPrefs()
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "sync-id backfill failed", e)
                            Toast.makeText(
                                context,
                                getString(R.string.nav_database_upgrade_failed_restart_the_app),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        backfillComplete = true
                    }
                    return@VehicleExpensesAutomatedTheme
                }

                val pageHelpController = rememberPageHelpController()

                // First-run splash when no *user* vehicles (S1/S4/S5).
                // null until Room Flow's first emission — do not treat collectAsState's
                // empty placeholder as "no vehicles" (that raced and forced welcome on every cold start).
                val allVehicles by produceState<List<Vehicle>?>(initialValue = null) {
                    vehicleRepository.getAllVehicles().collect { value = it }
                }
                val userVehiclesEmpty = remember(allVehicles) {
                    allVehicles?.forUserPicker()?.isEmpty()
                }

                // Dynamic page title
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                LaunchedEffect(backfillComplete, userVehiclesEmpty, currentRoute) {
                    if (!backfillComplete) return@LaunchedEffect
                    // null = vehicles not loaded yet; wait (do not open onboarding).
                    if (userVehiclesEmpty != true) return@LaunchedEffect
                    // Only auto-open splash from home so we don't interrupt tutorials mid-flow.
                    // (Manual "Show first-run welcome" from Help/Settings still works.)
                    if (currentRoute == "fill" || currentRoute == null) {
                        navController.navigate("onboarding") {
                            launchSingleTop = true
                        }
                    }
                }
                val title = when {
                    currentRoute == "fill" -> getString(R.string.nav_fill)
                    currentRoute == "triptracking" -> getString(R.string.nav_start_trip)
                    currentRoute == "managevehicles" -> getString(R.string.nav_manage_vehicles)
                    currentRoute == "expense" -> getString(R.string.nav_new_expense)
                    currentRoute?.startsWith("expense/") == true -> getString(R.string.nav_edit_expense)
                    currentRoute == "expenselist" -> getString(R.string.nav_expense_list)
                    currentRoute == "import" ||
                        currentRoute?.startsWith("import") == true -> getString(R.string.nav_import_old_pictures)
                    currentRoute == "reports_lab" ||
                        currentRoute?.startsWith("reports_lab/") == true -> getString(R.string.nav_reports)
                    currentRoute == "fuelhistory" -> getString(R.string.nav_fuel_history)
                    currentRoute?.startsWith("fuel/") == true -> getString(R.string.nav_edit_fill)
                    currentRoute == "settings" -> getString(R.string.nav_settings)
                    currentRoute == "syncing" -> getString(R.string.nav_syncing)
                    currentRoute == "settings/spreadsheet_sync" -> getString(R.string.nav_spreadsheet_sync)
                    currentRoute == "settings/photo_backup" -> getString(R.string.nav_photo_backup)
                    currentRoute == "help" -> getString(R.string.nav_help)
                    currentRoute == "about" -> getString(R.string.nav_about)
                    currentRoute == "onboarding" -> getString(R.string.nav_welcome)
                    currentRoute?.startsWith("tutorial/") == true -> getString(R.string.nav_setup_tips)
                    currentRoute == "experiment" ||
                        currentRoute?.startsWith("experiment?") == true -> getString(R.string.nav_alignment_experiment)
                    currentRoute == "experiment_pump" ||
                        currentRoute?.startsWith("experiment_pump") == true -> getString(R.string.nav_pump_experiment_long)
                    currentRoute == "experiment_heatmap_stage" -> getString(R.string.nav_heatmap_stage)
                    currentRoute == "experiment_multiscale_det" ||
                        currentRoute?.startsWith("experiment_multiscale_det") == true ->
                        getString(R.string.nav_dash_paddle_long_edge_det_rec)
                    else -> getString(R.string.app_name)
                }

                CompositionLocalProvider(LocalPageHelpController provides pageHelpController) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            // Experiment items push the list past short screens; allow vertical scroll.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                            Text(getString(R.string.app_name), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_fill)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("fill")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_start_trip)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("triptracking")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_manage_vehicles)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("managevehicles")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_new_expense)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("expense")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_reports)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("reports_lab")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_settings)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("settings")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_syncing)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("syncing")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_help)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("help")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text(getString(R.string.nav_about)) },
                                selected = false,
                                onClick = {
                                    navController.navigate("about")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            if (experimentsEnabled) {
                                NavigationDrawerItem(
                                    label = { Text(getString(R.string.nav_alignment_experiment)) },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment?auto=")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                NavigationDrawerItem(
                                    label = { Text(getString(R.string.nav_pump_experiment)) },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment_pump?auto=")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                NavigationDrawerItem(
                                    label = { Text(getString(R.string.nav_heatmap_stage)) },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment_heatmap_stage")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                NavigationDrawerItem(
                                    label = { Text(getString(R.string.nav_dash_paddle_long_edge_det_rec)) },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("experiment_multiscale_det?auto=")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                // Import Old Pictures is an experiment route.
                                NavigationDrawerItem(
                                    label = { Text(getString(R.string.nav_import_old_pictures)) },
                                    selected = false,
                                    onClick = {
                                        navController.navigate("import")
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                            } // scrollable Column
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            // Narrow phones (~448dp on 5556): short title so Info stays visible with ?N + !.
                            val screenWidthDp = LocalConfiguration.current.screenWidthDp
                            val narrowTitle = screenWidthDp < 600
                            val titleText = when {
                                title == getString(R.string.app_name) -> title
                                narrowTitle -> title
                                else -> getString(R.string.nav_title_app_page, title)
                            }
                            TopAppBar(
                                title = {
                                    Text(
                                        titleText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                },
                                actions = {
                                    // Priority if tight: drawer/back + Info (leading) already placed;
                                    // trailing badges only: ?N then !
                                    if (experimentsEnabled && pendingReviewCount > 0) {
                                        IconButton(
                                            onClick = {
                                                if (!experimentsEnabled) return@IconButton
                                                navController.navigate("import?review=1") {
                                                    launchSingleTop = true
                                                }
                                            },
                                            modifier = Modifier
                                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .semantics {
                                                    contentDescription = getString(R.string.nav_review_questions)
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
                                    // Leading: ☰ then ← then Info (reports children get both; hub ☰ only).
                                    val isReportsChild =
                                        currentRoute?.startsWith("reports_lab/") == true ||
                                            currentRoute == "expenselist"
                                    val isSettingsOrFuelSub =
                                        currentRoute == "settings/spreadsheet_sync" ||
                                            currentRoute == "settings/photo_backup" ||
                                            currentRoute?.startsWith("fuel/") == true
                                    // Settings/fuel keep ←-only; report children + everything else show drawer.
                                    val showMenu = !isSettingsOrFuelSub
                                    val showBack = isSettingsOrFuelSub || isReportsChild
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showMenu) {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } },
                                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                            ) {
                                                Icon(Icons.Default.Menu, contentDescription = getString(R.string.nav_menu))
                                            }
                                        }
                                        if (showBack) {
                                            IconButton(
                                                onClick = {
                                                    if (!navController.popBackStack() && isReportsChild) {
                                                        navController.navigate("reports_lab")
                                                    }
                                                },
                                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                            ) {
                                                Text("←")
                                            }
                                        }
                                        PageHelpTopBarAction(pageHelpController)
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        Surface(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = "fill",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("fill") { FillScreen(navController = navController) }
                                composable("managevehicles") { ManageVehicles2Screen(navController = navController) }
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

                                composable("triptracking") { TripTrackingScreen(navController = navController) }
                                composable("reports_lab") { ReportsLabHubScreen(navController = navController) }
                                composable("reports_lab/time") {
                                    ReportsLabTimeChartsScreen(navController = navController)
                                }
                                // Legacy deep links → unified time charts (R1.2)
                                composable("reports_lab/efficiency") {
                                    ReportsLabTimeChartsScreen(navController = navController)
                                }
                                composable("reports_lab/cost_trends") {
                                    ReportsLabTimeChartsScreen(navController = navController)
                                }
                                composable("reports_lab/monthly") {
                                    ReportsLabTimeChartsScreen(navController = navController)
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
                                composable("onboarding") {
                                    OnboardingSplashScreen(navController = navController)
                                }
                                composable(
                                    route = "tutorial/{tutorialId}",
                                    arguments = listOf(
                                        navArgument("tutorialId") { type = NavType.StringType },
                                    ),
                                ) { entry ->
                                    val id = entry.arguments?.getString("tutorialId").orEmpty()
                                    TutorialPagerScreen(navController = navController, tutorialId = id)
                                }
                                composable("help") { HelpScreen(navController = navController) }
                                composable("about") { AboutScreen() }
                            }
                        }
                    }
                }
                } // CompositionLocalProvider (PageHelp)
            }
            }
            }
        }
    }
}

@Composable
internal fun TransferBlockingScreen(state: DbState, progress: String) {
    val title = if (state is Importing) stringResource(R.string.startup_importing_data) else stringResource(R.string.startup_exporting_data)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(title)
            if (progress.isNotBlank()) Text(progress)
        }
    }
}

@Composable
private fun ExportFailedNotice(holder: DatabaseHealthState) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(holder) {
        holder.exportFailures.receiveAsFlow().collect { message = it }
    }
    val text = message ?: return
    AlertDialog(
        onDismissRequest = { message = null },
        confirmButton = {
            TextButton(onClick = { message = null }) { Text(stringResource(R.string.settings_ok)) }
        },
        text = { Text(stringResource(R.string.import_export_failed_1_s, text)) },
    )
}

fun stopExperimentForegroundService(context: Context) {
}
