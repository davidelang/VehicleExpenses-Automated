package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseListScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: ExpenseViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val expenses by viewModel.expenses.collectAsState()
    val vehicles by vehicleViewModel.vehicles.collectAsState(initial = emptyList())
    val vehicleNameById = remember(vehicles) { vehicles.associate { it.id to it.name } }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

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
            Text(
                "Tap a row to edit. Add new expenses from Menu → New Expense Entry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(expenses, key = { it.id }) { expense ->
            val vehicleName = vehicleNameById[expense.vehicleId] ?: "Vehicle ${expense.vehicleId}"
            val dateStr = dateFmt.format(Date(expense.date))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        navController?.navigate("expense/${expense.id}")
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "$vehicleName · $dateStr",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val vendorPart = expense.vendor.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                    Text(
                        "${vendorPart}${expense.description}".ifBlank { "(no description)" }
                    )
                    val odoPart = expense.odometer?.let { " · odo $it" } ?: ""
                    Text(
                        "${CurrencyCodes.formatAmount(expense.amount, expense.currency, defaultSymbol)} · " +
                            "${expense.category}$odoPart",
                        style = MaterialTheme.typography.bodySmall
                    )
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
