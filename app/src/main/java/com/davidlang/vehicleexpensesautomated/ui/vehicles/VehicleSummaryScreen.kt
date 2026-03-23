package com.davidlang.vehicleexpensesautomated.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun VehicleSummaryScreen(vehicleId: Int, navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Summary & Reports") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Reports for vehicle $vehicleId", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            Text("Fuel efficiency • Expenses • Monthly totals • Graphs", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { navController.popBackStack() }) { Text("Back to Quick Fillup") }
        }
    }
}
