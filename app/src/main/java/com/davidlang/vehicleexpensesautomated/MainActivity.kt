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
import com.davidlang.vehicleexpensesautomated.ui.dashboard.DashboardScreen
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelListScreen
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.vehicles.VehicleListScreen
import com.davidlang.vehicleexpensesautomated.sync.SyncWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        SyncWorker.startPeriodicSync(this)
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "quickfill") {  // ← NEW DEFAULT
        composable("quickfill") { QuickFillupScreen(navController) }
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
    }
}
