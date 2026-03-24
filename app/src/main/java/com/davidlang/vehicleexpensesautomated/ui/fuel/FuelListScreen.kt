package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel

@Composable
fun FuelListScreen(vehicleId: Int, vehicleName: String) {
    val viewModel: FuelViewModel = hiltViewModel()
    // rest of your FuelListScreen code unchanged
}
