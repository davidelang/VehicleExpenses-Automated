package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits

@Composable
fun FuelListScreen(navController: NavHostController? = null) {
    val viewModel: FuelViewModel = hiltViewModel()
    val fuelEntries by viewModel.fuelEntries.collectAsState()
    val context = LocalContext.current
    val unitLabel = remember {
        VolumeUnits.longLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Fuel Entries",
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
                    // DB value is preferred unit; label matches (no reconversion).
                    Text("Odometer: ${entry.odometer} | $unitLabel: ${entry.gallons} | Cost: $${entry.cost}")
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
