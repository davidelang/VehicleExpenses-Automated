package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel

private data class CatalogEntry(val title: String, val route: String, val blurb: String)

private val CATALOG = listOf(
    CatalogEntry(
        "Time based reports",
        "reports_lab/time",
        "mpg, G/mi, \$/G, \$/mi, monthly \$, trip miles/% · one chart · smooth bins",
    ),
    CatalogEntry("Expenses by category", "reports_lab/expenses", "Category totals and list"),
    CatalogEntry("Expenses list", "expenselist", "All expenses — tap to edit"),
    CatalogEntry("Fill history", "reports_lab/fills", "Chronological fills only (no trip starts)"),
    CatalogEntry("Vehicle summary", "reports_lab/vehicle_summary", "Shareable history pack"),
    CatalogEntry(
        "Trip miles",
        "reports_lab/trips",
        "Miles by type + trip start list (tap to edit)",
    ),
)

private const val HUB_INFO =
    "All-time summary of fuel and expenses (no filters on this screen). " +
        "Open a report set for vehicle and period filters, charts, and share. " +
        "Share on each report is one icon → TEXT, CSV, or PDF. " +
        "Not a tax-authority product — export miles and use elsewhere."

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
    val volumeLabel = remember {
        VolumeUnits.shortLabel(VolumeUnits.resolvedPreferredVolumeUnit(context))
    }

    // Hub summary is all-time — ignore reports_lab period/vehicle prefs (E1–E2)
    val allFuel = remember(fuels) { fuels.filter { !it.deleted } }
    val allExp = remember(expenses) { expenses.filter { !it.deleted } }
    val nameById = remember(vehicles) {
        vehicles.filter { !it.deleted }.associate { it.id to it.name } +
            (0 to "Unknown")
    }
    val overallLine = remember(allFuel, allExp, volumeLabel, defaultSymbol, defaultStored) {
        hubOverallSummaryLine(allFuel, allExp, volumeLabel, defaultSymbol, defaultStored)
    }
    val vehicleSummaries = remember(allFuel, allExp, nameById, defaultStored) {
        hubVehicleSummaries(allFuel, allExp, nameById, defaultStored)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReportsLabTitleRow(
            title = null,
            infoTitle = "Reports",
            infoText = HUB_INFO,
            shareActions = null,
        )

        if (vehicles.none { !it.deleted } && allFuel.isEmpty() && allExp.isEmpty()) {
            ReportsLabEmpty("No vehicles or rows yet. Add data, then revisit Reports.")
        }

        Text("Summary (all time)", style = MaterialTheme.typography.titleMedium)
        if (allFuel.isEmpty() && allExp.isEmpty()) {
            ReportsLabEmpty("No fuel or expenses yet.")
        } else {
            Text(
                overallLine,
                style = MaterialTheme.typography.bodyMedium,
                softWrap = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (vehicleSummaries.isNotEmpty()) {
                AdaptiveItemGrid(items = vehicleSummaries) { stats ->
                    Column(
                        modifier = Modifier
                            .wrapContentWidth(Alignment.Start)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(stats.name, style = MaterialTheme.typography.titleSmall, softWrap = true)
                        Text(
                            hubVehicleStatsLine(stats, volumeLabel, defaultSymbol),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = true,
                        )
                        Text(
                            hubVehicleExpenseLine(stats, defaultSymbol),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = true,
                        )
                    }
                }
            }
        }

        Text("Report sets", style = MaterialTheme.typography.titleMedium)
        AdaptiveItemGrid(items = CATALOG) { entry ->
            TappableCard(onClick = { navController.navigate(entry.route) }) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, softWrap = true, maxLines = 2)
                Text(entry.blurb, style = MaterialTheme.typography.bodySmall, softWrap = true, maxLines = 3)
            }
        }
    }
}
