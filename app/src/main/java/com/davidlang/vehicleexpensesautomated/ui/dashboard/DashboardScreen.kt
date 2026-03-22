package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val perVehicleSummary = viewModel.perVehicleSummary.collectAsState(initial = emptyList()).value

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Overall Summary", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vehicles: $totalVehicles")
                        Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                        Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                        Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                        Text("Avg Price/Gal: $${"%.2f".format(avgPricePerGallon)}")
                        Text("Rough Avg MPG: ${"%.1f".format(roughAvgMPG)}")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text("Per-Vehicle Breakdown", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(perVehicleSummary) { summary ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(summary.vehicle.name, style = MaterialTheme.typography.titleMedium)
                        Text("Expenses: $${"%.2f".format(summary.totalExpense)}")
                        Text("Fuel Cost: $${"%.2f".format(summary.totalFuelCost)}")
                        Text("Gallons: ${"%.1f".format(summary.totalGallons)}")
                    }
                }
            }

            if (perVehicleSummary.isEmpty()) {
                item {
                    Text("No vehicles or data yet.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
