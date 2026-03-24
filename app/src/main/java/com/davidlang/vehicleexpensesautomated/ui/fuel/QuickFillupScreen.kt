package com.davidlang.vehicleexpensesautomated.ui.fuel

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.davidlang.vehicleexpensesautomated.ui.fuel.FillupViewModel

@Composable
fun QuickFillupScreen(navController: NavController) {
    val viewModel: FillupViewModel = hiltViewModel()
    // rest of screen unchanged
}
