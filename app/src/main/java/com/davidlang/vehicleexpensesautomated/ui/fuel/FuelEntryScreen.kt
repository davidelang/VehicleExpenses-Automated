package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

@Composable
fun FuelEntryScreen(vehicleId: Int = 0, navController: NavHostController? = null) {
    val viewModel: FuelViewModel = hiltViewModel()

    var odometer by remember { mutableStateOf(0) }
    var gallons by remember { mutableStateOf(0.0) }
    var cost by remember { mutableStateOf(0.0) }
    var isPartialFill by remember { mutableStateOf(false) }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("New Fuel Entry", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = odometer.toString(), onValueChange = { odometer = it.toIntOrNull() ?: 0 }, label = { Text("Odometer") })
        OutlinedTextField(value = gallons.toString(), onValueChange = { gallons = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Gallons") })
        OutlinedTextField(value = cost.toString(), onValueChange = { cost = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Cost") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Partial Fill")
            Switch(checked = isPartialFill, onCheckedChange = { isPartialFill = it })
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { viewModel.saveFuel(FuelEntry(vehicleId = vehicleId, odometer = odometer, gallons = gallons, cost = cost, timestamp = timestamp, isPartialFill = isPartialFill)) }) {
            Text("Save Fuel Entry")
        }
    }
}
