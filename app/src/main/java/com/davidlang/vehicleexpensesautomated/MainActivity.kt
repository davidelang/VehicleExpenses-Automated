package com.davidlang.vehicleexpensesautomated

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.ui.about.AboutScreen
import com.davidlang.vehicleexpensesautomated.ui.dashboard.DashboardScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelListScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.vehicles.VehicleListScreen
import dagger.hilt.android.AndroidEntryPoint
import com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val repository: VehicleRepository by lazy { /* Hilt will inject later; for now we use a simple check */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // One-time default vehicle creation on first launch
        CoroutineScope(Dispatchers.IO).launch {
            if (repository.getAllVehicles().isEmpty()) {
                // Create default vehicle
                repository.insertVehicle(
                    com.davidlang.vehicleexpensesautomated.data.model.Vehicle(
                        make = "My",
                        model = "Vehicle",
                        year = 2025
                    )
                )
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("dashboard") { DashboardScreen() }
        composable("vehicles") { VehicleListScreen(navController) }
        composable("expenses/{vehicleId}/{vehicleName}") { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toInt() ?: 0
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: "Vehicle"
            ExpenseListScreen(vehicleId, vehicleName)
        }
        composable("fuel/{vehicleId}/{vehicleName}") { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getString("vehicleId")?.toInt() ?: 0
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: "Vehicle"
            FuelListScreen(vehicleId, vehicleName)
        }
        composable("settings") { SettingsScreen() }
        composable("about") { AboutScreen() }
    }
}

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vehicle Expenses Tracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController.navigate("dashboard") }) { Text("Dashboard") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("vehicles") }) { Text("View Vehicles") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("settings") }) { Text("Settings & Sync") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("about") }) { Text("About") }
    }
}
