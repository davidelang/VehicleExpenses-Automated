package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun FuelListScreen(vehicleId: Int? = null, vehicleName: String? = null) {
    val viewModel: FuelViewModel = hiltViewModel()
    val fuelEntries by viewModel.fuelEntries.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = vehicleName?.let { "Fuel for $it" } ?: "Fuel Entries",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(fuelEntries) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Odometer: ${entry.odometer} | Gallons: ${entry.gallons} | Cost: $${entry.cost}")
                    Text("Partial: ${entry.isPartialFill}")
                }
            }
        }
        if (fuelEntries.isEmpty()) {
            item {
                Text("No fuel entries yet", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
