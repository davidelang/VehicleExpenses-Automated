package com.davidlang.vehicleexpensesautomated.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

@Composable
fun DashboardScreen() {
    val viewModel: VehicleViewModel = hiltViewModel()
    // rest of your DashboardScreen code unchanged
}
