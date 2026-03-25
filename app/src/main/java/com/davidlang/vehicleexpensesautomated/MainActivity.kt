package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.ui.about.AboutScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseEntryScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.help.HelpScreen
import com.davidlang.vehicleexpensesautomated.ui.import.ImportOldPicturesScreen
import com.davidlang.vehicleexpensesautomated.ui.reports.ReportsScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import com.davidlang.vehicleexpensesautomated.ui.vehicle.AddNewVehicleScreen
import dagger.hilt.android.AndroidEntryPoint
import android.widget.Toast
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Vehicle Expenses", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)

                            NavigationDrawerItem(label = { Text("Quick Fill-up") }, selected = false, onClick = { navController.navigate("quickfill"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("New Vehicle") }, selected = false, onClick = { navController.navigate("newvehicle"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("New Expense Entry") }, selected = false, onClick = { navController.navigate("expense"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("Expense List") }, selected = false, onClick = { navController.navigate("expenselist"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("Import Old Pictures") }, selected = false, onClick = { navController.navigate("import"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("Reports & Charts") }, selected = false, onClick = { navController.navigate("reports"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("Help") }, selected = false, onClick = { navController.navigate("help"); scope.launch { drawerState.close() } })
                            NavigationDrawerItem(label = { Text("About") }, selected = false, onClick = { navController.navigate("about"); scope.launch { drawerState.close() } })
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Vehicle Expenses") },
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
                                composable("newvehicle") { AddNewVehicleScreen(navController = navController) }
                                composable("expense") { ExpenseEntryScreen(navController = navController) }
                                composable("expenselist") { ExpenseListScreen() }
                                composable("import") { ImportOldPicturesScreen(navController = navController) }
                                composable("reports") { ReportsScreen(navController = navController) }
                                composable("settings") { SettingsScreen() }
                                composable("help") { HelpScreen() }
                                composable("about") { AboutScreen() }
                            }
                        }
                    }
                }
            }
        }
    }
}
