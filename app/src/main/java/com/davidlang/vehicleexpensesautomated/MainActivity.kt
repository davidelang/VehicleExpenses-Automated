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
import com.davidlang.vehicleexpensesautomated.ui.conflict.ConflictResolutionScreen
import com.davidlang.vehicleexpensesautomated.ui.dashboard.DashboardScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelListScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.vehicles.VehicleListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        composable("conflict/{type}/{local}/{sheet}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val local = backStackEntry.arguments?.getString("local") ?: ""
            val sheet = backStackEntry.arguments?.getString("sheet") ?: ""
            ConflictResolutionScreen(
                conflictType = type,
                localData = local,
                sheetData = sheet,
                onResolve = { /* TODO: handle resolution in next step */ },
                navController = navController
            )
        }
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
