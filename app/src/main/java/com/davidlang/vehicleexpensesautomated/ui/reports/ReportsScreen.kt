package com.davidlang.vehicleexpensesautomated.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Eligible full fills for MPG: same vehicle, gallons>0, not partial, odo>0; consecutive strictly increasing odo. */
private data class VehicleMpgLine(
    val vehicleName: String,
    val lastLegMpg: Double?,
    val avgLegMpg: Double?
)

private fun eligibleFullFills(entries: List<FuelEntry>): List<FuelEntry> {
    return entries
        .filter { it.gallons > 0 && !it.isPartialFill && it.odometer > 0 }
        .sortedBy { it.timestamp }
}

/** Valid consecutive legs for one vehicle: mpg of each leg (odo delta / gallons of later fill). */
private fun mpgLegsForVehicle(entries: List<FuelEntry>): List<Double> {
    val full = eligibleFullFills(entries)
    if (full.size < 2) return emptyList()
    val legs = mutableListOf<Double>()
    for (i in 1 until full.size) {
        val prev = full[i - 1]
        val cur = full[i]
        if (cur.odometer > prev.odometer && cur.gallons > 0) {
            legs.add((cur.odometer - prev.odometer) / cur.gallons)
        }
    }
    return legs
}

private fun formatMpg(value: Double?): String {
    return if (value == null) "n/a" else "%.1f".format(value)
}

private fun formatEntryDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun ReportsScreen(navController: NavHostController) {
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()

    val expenses by expenseViewModel.expenses.collectAsState()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState()
    val vehicles by vehicleViewModel.vehicles.collectAsState()

    val vehicleNameById = remember(vehicles) {
        vehicles.associate { it.id to it.name }
    }

    val totalExpenses = expenses.sumOf { it.amount }
    val totalFuelCost = fuelEntries.sumOf { it.cost }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fuelEntries.count { it.isPartialFill }
    val totalFillUps = fuelEntries.size
    val dollarsPerGal = if (totalGallons > 0) totalFuelCost / totalGallons else null

    // Per-vehicle MPG only — never cross vehicleId (was the 583 mpg bug).
    val vehicleMpgLines = remember(fuelEntries, vehicleNameById) {
        val byVehicle = fuelEntries.groupBy { it.vehicleId }
        byVehicle.keys.sorted().map { vehicleId ->
            val name = vehicleNameById[vehicleId] ?: "Vehicle $vehicleId"
            val legs = mpgLegsForVehicle(byVehicle[vehicleId].orEmpty())
            VehicleMpgLine(
                vehicleName = name,
                lastLegMpg = legs.lastOrNull(),
                avgLegMpg = if (legs.isEmpty()) null else legs.average()
            )
        }
    }

    // DAO fuel list is ORDER BY timestamp DESC — take(5) = newest five.
    val recentFuel = remember(fuelEntries) { fuelEntries.take(5) }
    val maxRecentFuelCost = remember(recentFuel) {
        recentFuel.maxOfOrNull { it.cost }?.takeIf { it > 0 } ?: 1.0
    }

    val categoryTotals = expenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
    val recentExpenses = remember(expenses) { expenses.take(5) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Enhanced Reports & Charts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Overall Summary", style = MaterialTheme.typography.titleMedium)
                Text("Total Expenses: $${"%.2f".format(totalExpenses)}")
                Text("Total Fuel Cost: $${"%.2f".format(totalFuelCost)}")
                Text("Total Gallons: ${"%.1f".format(totalGallons)}")
                Text("Fill-ups: $totalFillUps (${partialFills} partial)")
                if (dollarsPerGal != null) {
                    Text("Average \$/gal: ${"%.2f".format(dollarsPerGal)}")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("MPG by vehicle", style = MaterialTheme.typography.titleSmall)
                if (vehicleMpgLines.isEmpty()) {
                    Text("n/a (no fuel entries)")
                } else {
                    vehicleMpgLines.forEach { line ->
                        Text(
                            "${line.vehicleName}: last ${formatMpg(line.lastLegMpg)} mpg · avg ${formatMpg(line.avgLegMpg)} mpg"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Fuel Cost Trends (newest 5)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        recentFuel.forEach { entry ->
            val barWidth = (entry.cost / maxRecentFuelCost).coerceIn(0.0, 1.0).toFloat()
            val name = vehicleNameById[entry.vehicleId] ?: "Vehicle ${entry.vehicleId}"
            val partialTag = if (entry.isPartialFill) " · partial" else ""
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "$name · ${formatEntryDate(entry.timestamp)} · odo ${entry.odometer}",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = if (entry.gallons <= 0) {
                        "Missed (0 gal)$partialTag"
                    } else {
                        "$${"%.2f".format(entry.cost)} · ${"%.2f".format(entry.gallons)} gal$partialTag"
                    }
                    Text(label, modifier = Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall)
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

        Text("Expenses by Category", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (categoryTotals.isEmpty()) {
            Text("No expenses", style = MaterialTheme.typography.bodyMedium)
        } else {
            categoryTotals.entries.forEach { (cat, total) ->
                val barWidth = (total / (totalExpenses + 0.01)).coerceIn(0.0, 1.0).toFloat()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
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

        Text("Recent Fuel Entries", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        recentFuel.forEach { entry ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val name = vehicleNameById[entry.vehicleId] ?: "Vehicle ${entry.vehicleId}"
                    Text("$name · ${formatEntryDate(entry.timestamp)}")
                    Text("Odo: ${entry.odometer} · Gal: ${"%.2f".format(entry.gallons)} · Cost: $${"%.2f".format(entry.cost)}")
                    if (entry.isPartialFill) {
                        Text("Partial fill", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Recent Expenses", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (recentExpenses.isEmpty()) {
            Text("No expenses", style = MaterialTheme.typography.bodyMedium)
        } else {
            recentExpenses.forEach { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${entry.description} | $${"%.2f".format(entry.amount)} | ${entry.category}")
                        Text(formatEntryDate(entry.date), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
