package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import com.davidlang.vehicleexpensesautomated.ui.about.AboutScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentAlignmentScreen
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentPaddleDynamicScreen
import com.davidlang.vehicleexpensesautomated.ui.experiment.ExperimentPumpScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.help.HelpScreen
import com.davidlang.vehicleexpensesautomated.ui.import.ImportOldPicturesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.ReportsScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
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

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission denied. Photo features will be disabled.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            VehicleExpensesAutomatedTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Dynamic page title
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val title = when (currentRoute) {
                    "quickfill" -> "Quick Fill-up"
                    "managevehicles" -> "Manage Vehicles"
                    "expense" -> "New Expense Entry"
                    "expenselist" -> "Expense List"
                    "import" -> "Import Old Pictures"
                    "reports" -> "Reports & Charts"
                    "settings" -> "Settings"
                    "help" -> "Help"
                    "about" -> "About"
                    "experiment" -> "Alignment Experiment"
                    "experiment_pump" -> "Gas Pump Extraction Experiment"
                    "experiment_dynamic" -> "Dynamic Detect Test"
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
                            NavigationDrawerItem(
                                label = { Text("Dynamic Detect Test") },
                                selected = false,
                                onClick = {
                                    navController.navigate("experiment_dynamic")
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(title) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                                composable("expense") { ExpenseEntryScreen(navController = navController) }
                                composable("expenselist") { ExpenseListScreen() }
                                composable("import") { ImportOldPicturesScreen(navController = navController) }
                                composable("reports") { ReportsScreen(navController = navController) }
                                composable("settings") { SettingsScreen() }
                                composable("help") { HelpScreen() }
                                composable("about") { AboutScreen() }
                                composable("experiment") { ExperimentAlignmentScreen(navController = navController) }
                                composable("experiment_pump") { ExperimentPumpScreen(navController = navController) }
                                composable("experiment_dynamic") { ExperimentPaddleDynamicScreen(navController = navController) }
                            }
                        }
                    }
                }
            }
        }
    }
}
