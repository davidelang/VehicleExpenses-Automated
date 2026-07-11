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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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

// --- Math helpers (leg = interim-gallon sum between consecutive full fills) ---

/** Full fill points: not partial, odometer > 0 (time-sorted ascending). */
private fun fullFillsAscending(entries: List<FuelEntry>): List<FuelEntry> {
    return entries
        .filter { !it.isPartialFill && it.odometer > 0 }
        .sortedBy { it.timestamp }
}

/**
 * One full-fill leg ending at [endFill] (must have a previous full).
 * cost/vol are rolled sums over (prev.ts, end.ts] with gallons > 0.
 */
private data class FullFillLeg(
    val endFill: FuelEntry,
    val sumCost: Double,
    val sumVol: Double,
    val mpg: Double
)

/**
 * Newest valid full-fill legs (newest first). Excludes first full (no predecessor).
 * Only legs with odo increase and volDisplay > 0 — every leg has defined mpg.
 */
private fun newestValidLegs(entries: List<FuelEntry>, maxLegs: Int = 5): List<FullFillLeg> {
    val full = fullFillsAscending(entries)
    if (full.size < 2) return emptyList()
    val legsAsc = mutableListOf<FullFillLeg>()
    for (i in 1 until full.size) {
        val prev = full[i - 1]
        val cur = full[i]
        if (cur.odometer <= prev.odometer) continue
        val window = entries.filter {
            it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp && it.gallons > 0
        }
        val sumVol = window.sumOf { it.gallons }
        if (sumVol <= 0) continue
        val sumCost = window.sumOf { it.cost }
        val mpg = (cur.odometer - prev.odometer) / sumVol
        legsAsc.add(
            FullFillLeg(
                endFill = cur,
                sumCost = sumCost,
                sumVol = sumVol,
                mpg = mpg
            )
        )
    }
    return legsAsc.asReversed().take(maxLegs)
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
    /** Up to 5 newest valid full-fill legs (each always has mpg). */
    val last5Legs: List<FullFillLeg>
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

/** Overall summary: 1 dense line (wraps naturally if narrow). No $/gal. */
private fun overallSummaryLine(
    totalExpenses: Double,
    totalFuelCost: Double,
    totalGallons: Double,
    unitLabel: String,
    totalFillUps: Int,
    partialFills: Int
): String {
    return "Exp ${formatMoney(totalExpenses)} · Fuel ${formatMoney(totalFuelCost)} · " +
        "${"%.1f".format(totalGallons)}$unitLabel · fills $totalFillUps (${partialFills}p)"
}

/** Stats only (no vehicle name) for Summary L2+. */
private fun vehicleStatsOnlyLine(stats: VehicleReportStats, unitLabel: String): String {
    val dpm = if (stats.dollarsPerMile == null) "n/a" else "%.3f".format(stats.dollarsPerMile)
    return "Fuel ${formatMoney(stats.fuelCost)} · " +
        "${"%.1f".format(stats.gallons)}$unitLabel · " +
        "${stats.fillCount}(${stats.partialCount}p) · " +
        "last ${formatMpg(stats.lastMpg)} · avg ${formatMpg(stats.avgMpg)} · $/mi $dpm"
}

/** Split stats at middot boundaries into two roughly equal parts. */
private fun splitStatsAtMiddot(stats: String): Pair<String, String> {
    val parts = stats.split(" · ")
    if (parts.size <= 1) return stats to ""
    val mid = (parts.size + 1) / 2
    return parts.take(mid).joinToString(" · ") to parts.drop(mid).joinToString(" · ")
}

private val vehicleColMinWidth = 156.dp
private val vehicleSummaryMinWidth = 200.dp
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
            val allLegsNewestFirst = newestValidLegs(vFuel, maxLegs = Int.MAX_VALUE)
            val legsChrono = allLegsNewestFirst.asReversed() // oldest→newest for avg/last
            VehicleReportStats(
                vehicleId = vehicleId,
                name = vehicleNameById[vehicleId] ?: "Vehicle $vehicleId",
                fuelCost = vFuel.sumOf { it.cost },
                gallons = vFuel.sumOf { it.gallons },
                fillCount = vFuel.size,
                partialCount = vFuel.count { it.isPartialFill },
                lastMpg = legsChrono.lastOrNull()?.mpg,
                avgMpg = if (legsChrono.isEmpty()) null else legsChrono.map { it.mpg }.average(),
                dollarsPerMile = dollarsPerMile(vFuel, vExp),
                last5Legs = allLegsNewestFirst.take(5)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Summary", style = MaterialTheme.typography.titleMedium)
                Text(
                    overallSummaryLine(
                        totalExpenses = totalExpenses,
                        totalFuelCost = totalFuelCost,
                        totalGallons = totalGallons,
                        unitLabel = volumeUnitLabel,
                        totalFillUps = totalFillUps,
                        partialFills = partialFills
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (vehicleStats.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val cols = ((maxWidth / vehicleSummaryMinWidth).toInt()).coerceAtLeast(1)
                        if (cols <= 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                vehicleStats.forEach { stats ->
                                    VehicleSummaryBlock(
                                        stats = stats,
                                        unitLabel = volumeUnitLabel,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            val chunked = vehicleStats.chunked(cols)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunked.forEach { rowVehicles ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowVehicles.forEach { stats ->
                                            VehicleSummaryBlock(
                                                stats = stats,
                                                unitLabel = volumeUnitLabel,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .widthIn(min = vehicleSummaryMinWidth)
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
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Last 5 full fills", style = MaterialTheme.typography.titleMedium)
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
                                VehicleLast5OnlyColumn(
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

/** Summary: name alone, then stats (1 line if fits, else 2 at middot split). */
@Composable
private fun VehicleSummaryBlock(
    stats: VehicleReportStats,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val statsLine = vehicleStatsOnlyLine(stats, unitLabel)
    Column(modifier = modifier) {
        Text(stats.name, style = MaterialTheme.typography.titleSmall)
        AdaptiveStatsText(
            statsLine = statsLine,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AdaptiveStatsText(
    statsLine: String,
    modifier: Modifier = Modifier
) {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(0)
        val measured = measurer.measure(
            text = statsLine,
            style = style,
            constraints = Constraints(maxWidth = maxPx)
        )
        val overflows = measured.lineCount > 1 || measured.didOverflowWidth
        if (!overflows) {
            Text(statsLine, style = style, modifier = Modifier.fillMaxWidth())
        } else {
            val (a, b) = splitStatsAtMiddot(statsLine)
            Column {
                Text(a, style = style, modifier = Modifier.fillMaxWidth())
                if (b.isNotEmpty()) {
                    Text(b, style = style, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** By-vehicle card: name header + last-5 valid full-fill legs only. */
@Composable
private fun VehicleLast5OnlyColumn(
    stats: VehicleReportStats,
    volumeUnitLabel: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stats.name, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            VehicleLast5List(
                legs = stats.last5Legs,
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
    legs: List<FullFillLeg>,
    volumeUnitLabel: String,
    modifier: Modifier = Modifier
) {
    val maxMpg = legs.maxOfOrNull { it.mpg }?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (legs.isEmpty()) {
            Text("No full fills", style = MaterialTheme.typography.bodySmall)
        } else {
            legs.forEach { leg ->
                val barFrac = (leg.mpg / maxMpg).coerceIn(0.0, 1.0).toFloat()
                FullFillLegRow(
                    leg = leg,
                    barFraction = barFrac,
                    volumeUnitLabel = volumeUnitLabel
                )
            }
        }
    }
}

@Composable
private fun FullFillLegRow(
    leg: FullFillLeg,
    barFraction: Float,
    volumeUnitLabel: String
) {
    val entry = leg.endFill
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
                "${formatMoney(leg.sumCost)} · ${formatVolume(leg.sumVol, volumeUnitLabel)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFF81C784).copy(alpha = 0.45f))
                )
                Text(
                    "mpg ${"%.1f".format(leg.mpg)}",
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
