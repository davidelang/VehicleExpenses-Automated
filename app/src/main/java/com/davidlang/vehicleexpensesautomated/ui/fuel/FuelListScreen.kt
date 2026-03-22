package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FuelListScreen(vehicleId: Int, vehicleName: String) {
    val viewModel: FuelViewModel = hiltViewModel(key = "fuel_$vehicleId")

    val fuelFills = viewModel.fuelFills.collectAsState(initial = emptyList()).value

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<FuelFill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$vehicleName Fuel Logs") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.LocalGasStation, contentDescription = "Add Fuel")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            items(fuelFills) { fill ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .combinedClickable(
                            onClick = { /* optional: edit */ },
                            onLongClick = { showDeleteConfirm = fill }
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocalGasStation,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).padding(end = 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$${String.format("%.2f", fill.totalCost)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("${String.format("%.1f", fill.gallons)} gal @ $${String.format("%.2f", fill.pricePerGallon)}/gal")
                            Text("Odometer: ${fill.odometer}")
                            Text(
                                Instant.ofEpochMilli(fill.dateMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = fill }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (fuelFills.isEmpty()) {
                item {
                    Text("No fuel fills logged yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Summary", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Gallons: ${String.format("%.1f", viewModel.totalGallons)}")
                        Text("Total Cost: $${String.format("%.2f", viewModel.totalCost)}")
                        Text("Avg Price/Gal: $${String.format("%.2f", viewModel.avgPricePerGallon)}")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFuelDialog(
            onSave = { gallons, pricePerGallon, odometer, dateMillis ->
                viewModel.addFuelFill(gallons, pricePerGallon, odometer, dateMillis)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    showDeleteConfirm?.let { fillToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Fuel Fill") },
            text = { Text("Are you sure you want to delete this fuel fill?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFuelFill(fillToDelete)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFuelDialog(
    onSave: (Double, Double, Int, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var gallons by remember { mutableStateOf("") }
    var pricePerGallon by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

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
                Button(onClick = { showDatePicker = true }) {
                    Text("Select Date")
                }
                Text("Selected Date: ${datePickerState.selectedDateMillis?.let { millis ->
                    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: "Not selected"}")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                gallons.toDoubleOrNull()?.let { gal ->
                    pricePerGallon.toDoubleOrNull()?.let { price ->
                        odometer.toIntOrNull()?.let { odo ->
                            if (gal > 0 && price >= 0 && odo >= 0) {
                                val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                                onSave(gal, price, odo, dateMillis)
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

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
