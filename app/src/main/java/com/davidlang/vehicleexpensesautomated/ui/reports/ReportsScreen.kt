package com.davidlang.vehicleexpensesautomated.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // Enhanced stats using exact model fields (all Double)
    val totalExpenses = expenses.sumOf { it.amount }
    val totalFuelCost = fuelEntries.sumOf { it.cost }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fuelEntries.count { it.isPartialFill }
    val totalFillUps = fuelEntries.size

    // MPG from last two full fills — ignore final partial + any missed fills between full fills
    val avgMpg = if (fuelEntries.size >= 2) {
        val sorted = fuelEntries.sortedBy { it.timestamp }
            .filter { !it.isPartialFill && !it.isMissedFillup }
        if (sorted.size >= 2) {
            val lastTwo = sorted.takeLast(2)
            if (lastTwo[1].odometer > lastTwo[0].odometer && lastTwo[1].gallons > 0)
                ((lastTwo[1].odometer - lastTwo[0].odometer) / lastTwo[1].gallons).toFloat()
            else 0f
        } else 0f
    } else 0f

    // Expense category breakdown
    val categoryTotals = expenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Enhanced Reports & Charts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Summary Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Overall Summary", style = MaterialTheme.typography.titleMedium)
                Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                Text("Fill-ups: $totalFillUps (${partialFills} partial)")
                Text("Avg MPG (last full fills): ${"%.1f".format(avgMpg)}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Fuel Trends – visual bars (Double -> Float for fillMaxWidth)
        Text("Fuel Cost Trends (last 5 entries)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.height(180.dp)) {
            items(fuelEntries.takeLast(5).reversed()) { entry ->
                val barWidth = (entry.cost / (totalFuelCost + 0.01)).coerceIn(0.0, 1.0).toFloat()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("$${entry.cost} (${entry.gallons} gal)", modifier = Modifier.width(120.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .fillMaxHeight()
                                .background(Color(0xFF4CAF50))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Expense Breakdown – visual bars (Double -> Float for fillMaxWidth)
        Text("Expenses by Category", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.height(180.dp)) {
            items(categoryTotals.entries.toList()) { (cat, total) ->
                val barWidth = (total / (totalExpenses + 0.01)).coerceIn(0.0, 1.0).toFloat()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("$cat: $${"%.2f".format(total)}", modifier = Modifier.width(140.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .fillMaxHeight()
                                .background(Color(0xFF2196F3))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Fuel
        Text("Recent Fuel Entries", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(fuelEntries.take(5)) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gallons: ${entry.gallons} | Cost: $${entry.cost} | Partial: ${entry.isPartialFill} | Missed: ${entry.isMissedFillup}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Expenses
        Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(expenses.take(5)) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${entry.description} | $${entry.amount} | ${entry.category}")
                    }
                }
            }
        }
    }
}
