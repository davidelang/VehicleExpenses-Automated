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
    val totalExpenses = viewModel.totalExpenses
    val totalFuelCost = viewModel.totalFuelCost
    val totalGallons = viewModel.totalGallons
    val avgPricePerGallon = viewModel.avgPricePerGallon
    val avgMPG = viewModel.avgMPG

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
                    Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                    Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                    Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                    Text("Avg Price/Gal: $${"%.2f".format(avgPricePerGallon)}")
                    Text("Rough Avg MPG: ${"%.1f".format(avgMPG)}")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("More stats coming soon (per-vehicle breakdown, charts, trends)")
        }
    }
}
