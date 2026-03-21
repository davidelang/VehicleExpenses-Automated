package com.davidlang.vehicleexpensesautomated.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

@Composable
fun VehicleListScreen(navController: NavController, viewModel: VehicleViewModel = hiltViewModel()) {
    val vehicles = viewModel.vehicles.collectAsState(initial = emptyList()).value

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Vehicles") })
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
            items(vehicles) { vehicle ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = {
                        navController.navigate("expenses/${vehicle.id}/${vehicle.make} ${vehicle.model}")
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${vehicle.year} ${vehicle.make} ${vehicle.model}", style = MaterialTheme.typography.titleMedium)
                            Text(vehicle.licensePlate, style = MaterialTheme.typography.bodyMedium)
                        }

                        Column {
                            IconButton(onClick = { navController.navigate("fuel/${vehicle.id}/${vehicle.make} ${vehicle.model}") }) {
                                Text("Fuel")
                            }
                            IconButton(onClick = { viewModel.deleteVehicle(vehicle) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete vehicle")
                            }
                        }
                    }
                }
            }

            if (vehicles.isEmpty()) {
                item {
                    Text("No vehicles yet. Tap + to add one!", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddVehicleDialog(
            onSave = { make, model, year, plate ->
                viewModel.addVehicle(make, model, year.toIntOrNull() ?: 2000, plate)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddVehicleDialog(
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = make,
                    onValueChange = { make = it },
                    label = { Text("Make") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("License Plate") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (make.isNotBlank() && model.isNotBlank() && year.isNotBlank() && plate.isNotBlank()) {
                    onSave(make, model, year, plate)
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
