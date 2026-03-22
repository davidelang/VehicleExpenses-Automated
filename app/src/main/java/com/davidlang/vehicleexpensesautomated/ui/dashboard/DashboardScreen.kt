package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val totalVehicles = viewModel.totalVehicles.collectAsState(initial = 0).value
    val totalExpenses = viewModel.totalExpenses.collectAsState(initial = 0.0).value
    val totalFuelCost = viewModel.totalFuelCost.collectAsState(initial = 0.0).value
    val totalGallons = viewModel.totalGallons.collectAsState(initial = 0.0).value
    val avgPricePerGallon = viewModel.avgPricePerGallon.collectAsState(initial = 0.0).value
    val roughAvgMPG = viewModel.roughAvgMPG.collectAsState(initial = 0.0).value

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Overall Summary", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Vehicles: $totalVehicles")
                    Text("Total Expenses: $${String.format("%.2f", totalExpenses)}")
                    Text("Total Fuel Cost: $${String.format("%.2f", totalFuelCost)}")
                    Text("Total Gallons: ${String.format("%.1f", totalGallons)}")
                    Text("Avg Price/Gal: $${String.format("%.2f", avgPricePerGallon)}")
                    Text("Rough Avg MPG: ${String.format("%.1f", roughAvgMPG)}")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("More stats coming soon (per-vehicle breakdown, charts, trends)")
        }
    }
}
