package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.EmptyStateText
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

private data class CatalogEntry(val title: String, val route: String, val blurb: String)

private val CATALOG = listOf(
    CatalogEntry("Fuel efficiency", "reports_lab/efficiency", "MPG legs, last/avg MPG, chart"),
    CatalogEntry("Fuel & cost trends", "reports_lab/cost_trends", "Unit price per fill and totals"),
    CatalogEntry("Monthly costs", "reports_lab/monthly", "Fuel vs other by month"),
    CatalogEntry("Expenses by category", "reports_lab/expenses", "Category totals and list"),
    CatalogEntry("Fill history", "reports_lab/fills", "Chronological fills for filters"),
    CatalogEntry("Vehicle summary", "reports_lab/vehicle_summary", "Shareable history pack"),
    CatalogEntry(
        "Trip miles",
        "reports_lab/trips",
        "Miles by trip type (open-only segments); share TEXT/CSV",
    ),
)

@Composable
fun ReportsLabHubScreen(navController: NavHostController) {
    val context = LocalContext.current
    val fuelVm: FuelViewModel = hiltViewModel()
    val expenseVm: ExpenseViewModel = hiltViewModel()
    val vehicleVm: VehicleViewModel = hiltViewModel()
    val fuels by fuelVm.fuelEntries.collectAsState()
    val expenses by expenseVm.expenses.collectAsState()
    val vehicles by vehicleVm.vehicles.collectAsState(initial = emptyList())
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val defaultStored = remember { CurrencyCodes.settingsDefaultStored(context) }
    var filter by remember { mutableStateOf(ReportsLabPrefs.load(context)) }

    val activeVehicles = remember(vehicles) {
        vehicles.filter {
            !it.deleted && it.id != 0 &&
                it.syncId != com.davidlang.vehicleexpensesautomated.data.repository.VehicleRepository.UNASSIGNED_VEHICLE_SYNC_ID
        }
    }
    val fFuel = remember(fuels, filter) { filterFuel(fuels, filter) }
    val fExp = remember(expenses, filter) { filterExpenses(expenses, filter) }
    val kpis = remember(fFuel, fExp, defaultStored) { teaserKpis(fFuel, fExp, defaultStored) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Reports Lab", style = MaterialTheme.typography.headlineMedium)
        ReportsLabBanner()
        Text(
            "Experimental report catalog with filters, charts, and share. Production Reports & Charts is unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReportsLabFilterBar(state = filter, vehicles = activeVehicles, onChange = { filter = it })

        if (activeVehicles.isEmpty() && fFuel.isEmpty() && fExp.isEmpty()) {
            ReportsLabEmpty("No vehicles or rows yet. Add data, then revisit Lab.")
        }

        Text("Teaser KPIs", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (fFuel.isEmpty() && fExp.isEmpty()) {
                    ReportsLabEmpty("No fuel or expenses in this filter.")
                } else {
                    Text("Fuel: ${CurrencyCodes.formatAggregateSum(kpis.fuelCostByCurrency, defaultSymbol)}")
                    Text("Expenses: ${CurrencyCodes.formatAggregateSum(kpis.expenseByCurrency, defaultSymbol)}")
                    Text("Fills: ${kpis.fillCount}")
                    Text(
                        "Last ${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()}: " +
                            "${formatMpg(kpis.lastMpg)} · Avg " +
                            "${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()}: " +
                            formatMpg(kpis.avgMpg),
                    )
                }
            }
        }

        Text("Report sets", style = MaterialTheme.typography.titleMedium)
        AdaptiveItemGrid(items = CATALOG) { entry ->
            TappableCard(onClick = { navController.navigate(entry.route) }) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, softWrap = true, maxLines = 2)
                Text(entry.blurb, style = MaterialTheme.typography.bodySmall, softWrap = true, maxLines = 3)
                Text("Open ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
