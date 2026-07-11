package com.davidlang.vehicleexpensesautomated.ui.reports

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Math helpers ---

/** Full fill points: not partial, odometer > 0 (time-sorted ascending). */
private fun fullFillsAscending(entries: List<FuelEntry>): List<FuelEntry> {
    return entries
        .filter { !it.isPartialFill && it.odometer > 0 }
        .sortedBy { it.timestamp }
}

/**
 * Leg MPG between consecutive full fills F_prev → F_cur:
 * (odo_cur - odo_prev) / sum(gallons of all fills with F_prev.ts < T <= F_cur.ts and gallons > 0).
 */
private fun mpgLegsForVehicle(entries: List<FuelEntry>): List<Double> {
    val full = fullFillsAscending(entries)
    if (full.size < 2) return emptyList()
    val legs = mutableListOf<Double>()
    for (i in 1 until full.size) {
        val prev = full[i - 1]
        val cur = full[i]
        if (cur.odometer <= prev.odometer) continue
        val gallonsSum = entries
            .filter { it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp && it.gallons > 0 }
            .sumOf { it.gallons }
        if (gallonsSum > 0) {
            legs.add((cur.odometer - prev.odometer) / gallonsSum)
        }
    }
    return legs
}

/** MPG of the leg ending at [fullFill], or null if n/a. */
private fun mpgForFullFill(allVehicleEntries: List<FuelEntry>, fullFill: FuelEntry): Double? {
    val full = fullFillsAscending(allVehicleEntries)
    val idx = full.indexOfFirst { it.id == fullFill.id }
    if (idx <= 0) return null
    val prev = full[idx - 1]
    val cur = full[idx]
    if (cur.odometer <= prev.odometer) return null
    val gallonsSum = allVehicleEntries
        .filter { it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp && it.gallons > 0 }
        .sumOf { it.gallons }
    if (gallonsSum <= 0) return null
    return (cur.odometer - prev.odometer) / gallonsSum
}

/**
 * $/mile = (sum fuel cost + sum expenses) / (maxOdo - minOdo)
 * over fills with odometer > 0; n/a if max ≤ min.
 */
private fun dollarsPerMile(
    fuelEntries: List<FuelEntry>,
    expenses: List<ExpenseEntry>
): Double? {
    val odos = fuelEntries.map { it.odometer }.filter { it > 0 }
    if (odos.size < 2) return null
    val minO = odos.minOrNull() ?: return null
    val maxO = odos.maxOrNull() ?: return null
    if (maxO <= minO) return null
    val fuelCost = fuelEntries.sumOf { it.cost }
    val expCost = expenses.sumOf { it.amount }
    return (fuelCost + expCost) / (maxO - minO).toDouble()
}

private data class VehicleReportStats(
    val vehicleId: Int,
    val name: String,
    val fuelCost: Double,
    val gallons: Double,
    val fillCount: Int,
    val partialCount: Int,
    val lastMpg: Double?,
    val avgMpg: Double?,
    val dollarsPerMile: Double?,
    val last5Full: List<FuelEntry>,
    /** Leg MPG ending at each last-5 full fill id. */
    val mpgByEntryId: Map<Long, Double?>
)

private fun formatMpg(value: Double?): String {
    return if (value == null) "n/a" else "%.1f".format(value)
}

private fun formatMoney(value: Double): String = "$" + "%.2f".format(value)

private fun formatEntryDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatVolume(gallons: Double, unitLabel: String): String {
    return "%.2f%s".format(gallons, unitLabel)
}

private val vehicleColMinWidth = 156.dp
private val vehicleColMaxHeight = 280.dp

@Composable
fun ReportsScreen(navController: NavHostController) {
    val expenseViewModel: ExpenseViewModel = hiltViewModel()
    val fuelViewModel: FuelViewModel = hiltViewModel()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()
    val context = LocalContext.current

    val expenses by expenseViewModel.expenses.collectAsState()
    val fuelEntries by fuelViewModel.fuelEntries.collectAsState()
    val vehicles by vehicleViewModel.vehicles.collectAsState()

    val volumeUnitLabel = remember {
        val prefs = context.getSharedPreferences("vehicle_settings", Context.MODE_PRIVATE)
        val u = prefs.getString("volume_unit", "G") ?: "G"
        if (u == "L") "L" else "G"
    }

    val vehicleNameById = remember(vehicles) {
        vehicles.associate { it.id to it.name }
    }

    val totalExpenses = expenses.sumOf { it.amount }
    val totalFuelCost = fuelEntries.sumOf { it.cost }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fuelEntries.count { it.isPartialFill }
    val totalFillUps = fuelEntries.size

    val vehicleStats = remember(fuelEntries, expenses, vehicleNameById) {
        val fuelByV = fuelEntries.groupBy { it.vehicleId }
        val expByV = expenses.groupBy { it.vehicleId }
        val ids = (fuelByV.keys + expByV.keys).toSortedSet()
        ids.map { vehicleId ->
            val vFuel = fuelByV[vehicleId].orEmpty()
            val vExp = expByV[vehicleId].orEmpty()
            val legs = mpgLegsForVehicle(vFuel)
            val fullDesc = fullFillsAscending(vFuel).asReversed() // newest first
            val last5 = fullDesc.take(5)
            val mpgMap = last5.associate { it.id to mpgForFullFill(vFuel, it) }
            VehicleReportStats(
                vehicleId = vehicleId,
                name = vehicleNameById[vehicleId] ?: "Vehicle $vehicleId",
                fuelCost = vFuel.sumOf { it.cost },
                gallons = vFuel.sumOf { it.gallons },
                fillCount = vFuel.size,
                partialCount = vFuel.count { it.isPartialFill },
                lastMpg = legs.lastOrNull(),
                avgMpg = if (legs.isEmpty()) null else legs.average(),
                dollarsPerMile = dollarsPerMile(vFuel, vExp),
                last5Full = last5,
                mpgByEntryId = mpgMap
            )
        }
    }

    val allFillsNewest = remember(fuelEntries) {
        fuelEntries.sortedByDescending { it.timestamp }.let { if (it.size > 50) it.take(50) else it }
    }
    val allExpensesNewest = remember(expenses) {
        expenses.sortedByDescending { it.date }.let { if (it.size > 50) it.take(50) else it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Reports", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Overall", style = MaterialTheme.typography.titleMedium)
                Text("Expenses: ${formatMoney(totalExpenses)}")
                Text("Fuel: ${formatMoney(totalFuelCost)}")
                Text("Gallons: ${"%.1f".format(totalGallons)}")
                Text("Fills: $totalFillUps ($partialFills partial)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("By vehicle", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (vehicleStats.isEmpty()) {
            Text("No vehicles with data", style = MaterialTheme.typography.bodyMedium)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = ((maxWidth / vehicleColMinWidth).toInt()).coerceAtLeast(1)
                val chunked = vehicleStats.chunked(cols)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunked.forEach { rowVehicles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowVehicles.forEach { stats ->
                                VehicleColumn(
                                    stats = stats,
                                    volumeUnitLabel = volumeUnitLabel,
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = vehicleColMinWidth)
                                )
                            }
                            repeat(cols - rowVehicles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= vehicleColMinWidth * 2
            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpensesBlock(
                        expenses = allExpensesNewest,
                        modifier = Modifier.weight(1f)
                    )
                    FillsBlock(
                        fills = allFillsNewest,
                        vehicleNameById = vehicleNameById,
                        volumeUnitLabel = volumeUnitLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpensesBlock(expenses = allExpensesNewest, modifier = Modifier.fillMaxWidth())
                    FillsBlock(
                        fills = allFillsNewest,
                        vehicleNameById = vehicleNameById,
                        volumeUnitLabel = volumeUnitLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleColumn(
    stats: VehicleReportStats,
    volumeUnitLabel: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stats.name, style = MaterialTheme.typography.titleSmall)
            Text("Fuel: ${formatMoney(stats.fuelCost)}")
            Text("Gal: ${"%.1f".format(stats.gallons)}")
            Text("Fills: ${stats.fillCount} (${stats.partialCount} partial)")
            Text("Last MPG: ${formatMpg(stats.lastMpg)}")
            Text("Avg MPG: ${formatMpg(stats.avgMpg)}")
            Text(
                "$/mi: " + if (stats.dollarsPerMile == null) "n/a"
                else "%.3f".format(stats.dollarsPerMile)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last 5 full fills", style = MaterialTheme.typography.labelLarge)
            VehicleLast5List(
                last5 = stats.last5Full,
                mpgByEntryId = stats.mpgByEntryId,
                volumeUnitLabel = volumeUnitLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = vehicleColMaxHeight)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun VehicleLast5List(
    last5: List<FuelEntry>,
    mpgByEntryId: Map<Long, Double?>,
    volumeUnitLabel: String,
    modifier: Modifier = Modifier
) {
    val maxMpg = mpgByEntryId.values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (last5.isEmpty()) {
            Text("No full fills", style = MaterialTheme.typography.bodySmall)
        } else {
            last5.forEach { entry ->
                val mpg = mpgByEntryId[entry.id]
                val barFrac = if (mpg != null) {
                    (mpg / maxMpg).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                FullFillRow(
                    entry = entry,
                    mpg = mpg,
                    barFraction = barFrac,
                    volumeUnitLabel = volumeUnitLabel
                )
            }
        }
    }
}

@Composable
private fun FullFillRow(
    entry: FuelEntry,
    mpg: Double?,
    barFraction: Float,
    volumeUnitLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${formatEntryDate(entry.timestamp)} · odo ${entry.odometer}",
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${formatMoney(entry.cost)} · ${formatVolume(entry.gallons, volumeUnitLabel)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (mpg != null && barFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFF81C784).copy(alpha = 0.45f))
                    )
                }
                Text(
                    "mpg ${formatMpg(mpg)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpensesBlock(
    expenses: List<ExpenseEntry>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Expenses", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (expenses.isEmpty()) {
            Text("No expenses", style = MaterialTheme.typography.bodyMedium)
        } else {
            expenses.forEach { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${entry.description} · ${formatMoney(entry.amount)}")
                        Text(
                            "${formatEntryDate(entry.date)} · ${entry.category}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FillsBlock(
    fills: List<FuelEntry>,
    vehicleNameById: Map<Int, String>,
    volumeUnitLabel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("All fills (by date)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (fills.isEmpty()) {
            Text("No fuel entries", style = MaterialTheme.typography.bodyMedium)
        } else {
            fills.forEach { entry ->
                val name = vehicleNameById[entry.vehicleId] ?: "Vehicle ${entry.vehicleId}"
                val partial = if (entry.isPartialFill) " · partial" else ""
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("$name · ${formatEntryDate(entry.timestamp)}$partial")
                        Text(
                            "odo ${entry.odometer} · ${formatMoney(entry.cost)} · ${formatVolume(entry.gallons, volumeUnitLabel)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
