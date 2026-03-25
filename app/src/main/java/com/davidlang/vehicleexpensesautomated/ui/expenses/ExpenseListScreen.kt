package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun ExpenseListScreen(navController: NavHostController? = null) {
    val viewModel: ExpenseViewModel = hiltViewModel()
    // TODO: replace with real list once repository exposes getAllExpenses()
    val dummyExpenses = listOf("Expense #1 - $45.67", "Expense #2 - $12.34")

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
        items(dummyExpenses) { expense ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = expense,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
