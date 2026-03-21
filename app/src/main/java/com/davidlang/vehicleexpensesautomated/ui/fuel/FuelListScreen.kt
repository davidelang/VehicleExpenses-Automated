package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FuelListScreen(vehicleId: Int, vehicleName: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fuel Logs for $vehicleName (ID: $vehicleId)")
        Spacer(modifier = Modifier.height(16.dp))
        Text("Placeholder - fuel logging will go here")
    }
}
