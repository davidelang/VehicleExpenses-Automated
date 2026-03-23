package com.davidlang.vehicleexpensesautomated.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.data.repository.FuelRepository
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
import kotlinx.coroutines.flow.collectLatest

@Composable
fun VehicleSummaryScreen(vehicleId: Int, navController: NavController) {
    val repository = remember { FuelRepository() }
    var fillups by remember { mutableStateOf<List<FuelFillup>>(emptyList()) }

    LaunchedEffect(vehicleId) {
        repository.getFillupsForVehicle(vehicleId).collectLatest { fillups = it }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Reports - Vehicle $vehicleId") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Recent Fillups (${fillups.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (fillups.isEmpty()) {
                Text("No fillups yet — take your first photo!")
            } else {
                fillups.take(5).forEach { f ->
                    Text("• ${f.gallons} gal @ \$$${f.cost} (odo ${f.odometer})")
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Quick Fillup")
            }
        }
    }
}
