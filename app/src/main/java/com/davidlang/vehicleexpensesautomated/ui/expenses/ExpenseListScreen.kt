package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel

@Composable
fun ExpenseListScreen(vehicleId: Int, vehicleName: String) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    // rest of your ExpenseListScreen code unchanged
}
