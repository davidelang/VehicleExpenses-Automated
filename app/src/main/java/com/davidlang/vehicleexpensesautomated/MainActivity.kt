package com.davidlang.vehicleexpensesautomated

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.data.sync.SyncManager
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.vehicles.VehicleSummaryScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val syncManager = hiltViewModel<SyncManager>() // injected via Hilt
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "quickfill") {
        composable("quickfill") {
            QuickFillupScreen(navController)
            // Floating Sync button (visible on every screen)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                FloatingActionButton(
                    onClick = {
                        isSyncing = true
                        scope.launch {
                            syncManager.triggerImmediateSync()
                            isSyncing = false
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Sync Now")
                    }
                }
            }
        }
        composable("reports/{vehicleId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("vehicleId")?.toInt() ?: 0
            VehicleSummaryScreen(id, navController)
        }
        // TODO: add more routes later (expenses, vehicles, settings)
    }
}
