package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun ExpenseListScreen(navController: NavHostController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    val expenses by viewModel.expenses.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Expense List",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(expenses) { expense ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${expense.description} | $${expense.amount} | ${expense.category}")
                }
            }
        }
        if (expenses.isEmpty()) {
            item {
                Text("No expenses yet", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
