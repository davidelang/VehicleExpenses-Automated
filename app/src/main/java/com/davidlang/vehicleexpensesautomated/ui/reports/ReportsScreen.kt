package com.davidlang.vehicleexpensesautomated.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

@Composable
fun ReportsScreen(navController: NavHostController) {
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("Advanced Reports & Charts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Vehicles & Summary", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(vehicles) { vehicle ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${vehicle.make} ${vehicle.model} (${vehicle.year})")
                        Text("License: ${vehicle.licensePlate ?: "—"}")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Charts coming soon (fuel efficiency, expense trends, etc.)", style = MaterialTheme.typography.bodyMedium)
    }
}
