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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.davidlang.vehicleexpensesautomated.ui.settings.PhotoBackupScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SpreadsheetSyncScreen
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
        if (!saveFuelPhotos) return
        val permission = mediaImagesPermission()
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        mediaPermissionLauncher.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Camera only here — media follows in cameraPermissionLauncher callback (no stacked dialogs).
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            VehicleExpensesAutomatedTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                var backfillComplete by remember { mutableStateOf(syncIdBackfill.isBackfillDone()) }
                var syncFailureVisible by remember { mutableStateOf(false) }

                LaunchedEffect(backfillComplete, navController.currentBackStackEntry) {
                    syncFailureVisible = SyncFailureStore(context).hasAnyFailure()
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

                // Dynamic page title
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val title = when {
                    currentRoute == "quickfill" -> "Quick Fill-up"
                    currentRoute == "managevehicles" -> "Manage Vehicles"
                    currentRoute == "expense" -> "New Expense Entry"
                    currentRoute?.startsWith("expense/") == true -> "Edit Expense"
                    currentRoute == "expenselist" -> "Expense List"
                    currentRoute == "import" -> "Import Old Pictures"
                    currentRoute == "reports" -> "Reports & Charts"
                    currentRoute == "settings" -> "Settings"
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
                                label = { Text("Settings") },
                                selected = false,
                                onClick = {
                                    navController.navigate("settings")
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
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(if (title == "Vehicle Expenses") title else "Vehicle Expenses - $title") },
                                actions = {
                                    if (syncFailureVisible) {
                                        IconButton(onClick = { navController.navigate("settings") }) {
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
                                        currentRoute == "settings/photo_backup"
                                    if (isSettingsSubRoute) {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Text("←")
                                        }
                                    } else {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                                composable("import") { ImportOldPicturesScreen(navController = navController) }
                                composable("reports") { ReportsScreen(navController = navController) }
                                composable("settings") { SettingsScreen(navController = navController) }
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
