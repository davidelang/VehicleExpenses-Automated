package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FuelListScreen(vehicleId: Int, vehicleName: String) {
    val viewModel: FuelViewModel = hiltViewModel(key = "fuel_$vehicleId")

    val fuelFills = viewModel.fuelFills.collectAsState(initial = emptyList()).value

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("$vehicleName Fuel Logs") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            items(fuelFills) { fill ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("$${String.format("%.2f", fill.totalCost)}", style = MaterialTheme.typography.titleMedium)
                        Text("${String.format("%.1f", fill.gallons)} gal @ $${String.format("%.2f", fill.pricePerGallon)}/gal")
                        Text("Odometer: ${fill.odometer}")
                        Text(
                            Instant.ofEpochMilli(fill.dateMillis)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (fuelFills.isEmpty()) {
                item {
                    Text("No fuel fills logged yet.", modifier = Modifier.padding(16.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Summary", style = MaterialTheme.typography.titleLarge)
                Text("Total Gallons: ${String.format("%.1f", viewModel.totalGallons)}")
                Text("Total Cost: $${String.format("%.2f", viewModel.totalCost)}")
                Text("Avg Price/Gal: $${String.format("%.2f", viewModel.avgPricePerGallon)}")
            }
        }
    }

    if (showAddDialog) {
        AddFuelDialog(
            onSave = { gallons, pricePerGallon, odometer ->
                viewModel.addFuelFill(gallons, pricePerGallon, odometer, System.currentTimeMillis())
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddFuelDialog(
    onSave: (Double, Double, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var gallons by remember { mutableStateOf("") }
    var pricePerGallon by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Fuel Fill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = gallons,
                    onValueChange = { gallons = it },
                    label = { Text("Gallons") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pricePerGallon,
                    onValueChange = { pricePerGallon = it },
                    label = { Text("Price per Gallon ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text("Odometer Reading") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                gallons.toDoubleOrNull()?.let { gal ->
                    pricePerGallon.toDoubleOrNull()?.let { price ->
                        odometer.toIntOrNull()?.let { odo ->
                            if (gal > 0 && price >= 0 && odo >= 0) {
                                onSave(gal, price, odo)
                            }
                        }
                    }
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
