package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry

@Composable
fun ExpenseEntryScreen(navController: NavHostController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()

    var vehicleId by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf(0.0) }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("New Expense", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = vehicleId.toString(), onValueChange = { vehicleId = it.toIntOrNull() ?: 0 }, label = { Text("Vehicle ID") })
        OutlinedTextField(value = amount.toString(), onValueChange = { amount = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Amount") })
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { viewModel.saveExpense(ExpenseEntry(vehicleId = vehicleId, amount = amount, description = description, category = category, date = date)) }) {
            Text("Save Expense")
        }
    }
}
