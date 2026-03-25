package com.davidlang.vehicleexpensesautomated.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel

@Composable
fun ReportsScreen(navController: NavHostController) {
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val fuelViewModel: FuelViewModel = hiltViewModel()

    val expenses by expenseViewModel.expenses.collectAsState()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState()

    val totalExpenses = expenses.sumOf { it.amount }
    val totalFuelCost = fuelEntries.sumOf { it.cost }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fuelEntries.count { it.isPartialFill }
    val totalFillUps = fuelEntries.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Advanced Reports & Charts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Summary", style = MaterialTheme.typography.titleMedium)
                Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                Text("Fill-ups: $totalFillUps (${partialFills} partial)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Recent Fuel Entries", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(fuelEntries.take(5)) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gallons: ${entry.gallons} | Cost: $${entry.cost}")
                        Text("Partial: ${entry.isPartialFill}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(expenses.take(5)) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${entry.description} | $${entry.amount}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Charts coming soon (fuel efficiency, expense trends, etc.)", style = MaterialTheme.typography.bodyMedium)
    }
}
