package com.davidlang.vehicleexpensesautomated.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.EmptyStateText
import com.davidlang.vehicleexpensesautomated.ui.components.FeatureScreenHeader
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureScreenHeader(
            title = "Expense list",
            subtitle = "Tap a card to edit. Add from Menu → New expense, or Reports hub.",
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (expenses.isEmpty()) {
            EmptyStateText("No expenses yet")
        } else {
            AdaptiveItemGrid(items = expenses) { expense ->
                val vehicleName = vehicleNameById[expense.vehicleId] ?: "Vehicle ${expense.vehicleId}"
                val dateStr = dateFmt.format(Date(expense.date))
                TappableCard(
                    onClick = { navController?.navigate("expense/${expense.id}") },
                ) {
                    Text(
                        "$vehicleName · $dateStr",
                        style = MaterialTheme.typography.titleSmall,
                        softWrap = true,
                        maxLines = 2,
                    )
                    val vendorPart = expense.vendor.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
                    Text(
                        "${vendorPart}${expense.description}".ifBlank { "(no description)" },
                        softWrap = true,
                        maxLines = 3,
                    )
                    val odoPart = expense.odometer?.let { " · odo $it" } ?: ""
                    Text(
                        "${CurrencyCodes.formatAmount(expense.amount, expense.currency, defaultSymbol)} · " +
                            "${expense.category}$odoPart",
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = true,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
