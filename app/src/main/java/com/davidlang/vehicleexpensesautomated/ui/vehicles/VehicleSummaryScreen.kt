package com.davidlang.vehicleexpensesautomated.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.ui.fuel.FillupViewModel

@Composable
fun VehicleSummaryScreen(vehicleId: Int, navController: NavController) {
    val viewModel: FillupViewModel = hiltViewModel()
    // rest of your VehicleSummaryScreen code unchanged
}
