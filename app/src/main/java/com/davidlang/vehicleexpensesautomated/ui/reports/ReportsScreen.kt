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
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
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
    val sumCostByCurrency: Map<String, Double>,
    val sumVol: Double,
    val mpg: Double
)

/**
 * Newest valid full-fill legs (newest first). Excludes first full (no predecessor).
 * Only legs with odo increase and volDisplay > 0 — every leg has defined mpg.
 */
private fun newestValidLegs(
    entries: List<FuelEntry>,
    defaultStored: String,
    maxLegs: Int = 5,
): List<FullFillLeg> {
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
        val sumCostByCurrency = CurrencyCodes.sumByCurrency(
            window,
            defaultStored,
            { it.currency },
            { it.cost },
        )
        val mpg = (cur.odometer - prev.odometer) / sumVol
        legsAsc.add(
            FullFillLeg(
                endFill = cur,
                sumCostByCurrency = sumCostByCurrency,
                sumVol = sumVol,
                mpg = mpg
            )
        )
    }
    return legsAsc.asReversed().take(maxLegs)
}

/**
 * **$/mi** = (sum of fuel cost + sum of expenses for this vehicle)
 * / (maxOdo − minOdo) over all fuel rows with `odometer > 0`.
 *
 * Partial fills at the **start or end** of the odo range are acceptable noise
 * (they still contribute min/max if odo > 0). Mid-trip partials do not need
 * special handling: they do not change max−min when odometers still bound the range.
 * Denominator is **not** restricted to full fills only.
 *
 * @return n/a (null) if fewer than two positive odometers or max ≤ min.
 */
private fun dollarsPerMile(
    fuelEntries: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): Double? {
    val odos = fuelEntries.map { it.odometer }.filter { it > 0 }
    if (odos.size < 2) return null
    val minO = odos.minOrNull() ?: return null
    val maxO = odos.maxOrNull() ?: return null
    if (maxO <= minO) return null
    val fuelSums = CurrencyCodes.sumByCurrency(
        fuelEntries,
        defaultStored,
        { it.currency },
        { it.cost },
    )
    val expSums = CurrencyCodes.sumByCurrency(
        expenses,
        defaultStored,
        { it.currency },
        { it.amount },
    )
    val combined = mutableMapOf<String, Double>()
    for ((k, v) in fuelSums) combined[k] = combined.getOrDefault(k, 0.0) + v
    for ((k, v) in expSums) combined[k] = combined.getOrDefault(k, 0.0) + v
    if (combined.size != 1) return null
    val totalCost = combined.values.first()
    return totalCost / (maxO - minO).toDouble()
}

private data class VehicleReportStats(
    val vehicleId: Int,
    val name: String,
    val fuelCostByCurrency: Map<String, Double>,
    val gallons: Double,
    val fillCount: Int,
    val partialCount: Int,
    val lastMpg: Double?,
    val avgMpg: Double?,
    val dollarsPerMile: Double?,
    /** Up to 5 newest valid full-fill legs (each always has mpg). */
    val last5Legs: List<FullFillLeg>,
    val expenseTotalByCurrency: Map<String, Double>,
    /** Category → currency → sum amount for this vehicle. */
    val expensesByCategory: Map<String, Map<String, Double>>
)

private fun formatMpg(value: Double?): String {
    return if (value == null) "n/a" else "%.1f".format(value)
}

private fun formatEntryDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatVolume(gallons: Double, unitLabel: String): String {
    return "%.2f%s".format(gallons, unitLabel)
}

/** Overall summary: 1 dense line (wraps naturally if narrow). No $/gal. */
private fun overallSummaryLine(
    totalExpensesByCurrency: Map<String, Double>,
    totalFuelCostByCurrency: Map<String, Double>,
    totalGallons: Double,
    unitLabel: String,
    totalFillUps: Int,
    partialFills: Int,
    defaultSymbol: String,
): String {
    val exp = CurrencyCodes.formatAggregateSum(totalExpensesByCurrency, defaultSymbol)
    val fuel = CurrencyCodes.formatAggregateSum(totalFuelCostByCurrency, defaultSymbol)
    return "Exp $exp · Fuel $fuel · " +
        "${"%.1f".format(totalGallons)}$unitLabel · fills $totalFillUps (${partialFills}p)"
}

/** Stats only (no vehicle name) for Summary L2+. */
private fun vehicleStatsOnlyLine(
    stats: VehicleReportStats,
    unitLabel: String,
    defaultSymbol: String,
): String {
    val dpm = if (stats.dollarsPerMile == null) "n/a" else "%.3f".format(stats.dollarsPerMile)
    val fuel = CurrencyCodes.formatAggregateSum(stats.fuelCostByCurrency, defaultSymbol)
    return "Fuel $fuel · " +
        "${"%.1f".format(stats.gallons)}$unitLabel · " +
        "${stats.fillCount}(${stats.partialCount}p) · " +
        "last ${formatMpg(stats.lastMpg)} · avg ${formatMpg(stats.avgMpg)} · $/mi $dpm"
}

/** Exp total + category breakdown (compact). */
private fun vehicleExpenseSummaryLine(stats: VehicleReportStats, defaultSymbol: String): String {
    val total = CurrencyCodes.formatAggregateSum(stats.expenseTotalByCurrency, defaultSymbol)
    if (stats.expensesByCategory.isEmpty()) {
        return "Exp $total"
    }
    val cats = stats.expensesByCategory.entries
        .sortedByDescending { (_, byCur) -> byCur.values.sum() }
        .joinToString(" · ") { (cat, byCur) ->
            val amt = CurrencyCodes.formatAggregateSum(byCur, defaultSymbol)
            "${cat.ifBlank { "Other" }} $amt"
        }
    return "Exp $total · $cats"
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

    // DB volumes are already in preferred unit — label only, no reconversion.
    val volumeUnitLabel = remember {
        com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.shortLabel(
            com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.resolvedPreferredVolumeUnit(context)
        )
    }
    val defaultSymbol = remember { CurrencyCodes.settingsDefaultSymbol(context) }
    val defaultStored = remember { CurrencyCodes.settingsDefaultStored(context) }

    val vehicleNameById = remember(vehicles) {
        vehicles.associate { it.id to it.name }
    }

    val totalExpensesByCurrency = remember(expenses, defaultStored) {
        CurrencyCodes.sumByCurrency(expenses, defaultStored, { it.currency }, { it.amount })
    }
    val totalFuelCostByCurrency = remember(fuelEntries, defaultStored) {
        CurrencyCodes.sumByCurrency(fuelEntries, defaultStored, { it.currency }, { it.cost })
    }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fuelEntries.count { it.isPartialFill }
    val totalFillUps = fuelEntries.size

    val vehicleStats = remember(fuelEntries, expenses, vehicleNameById, defaultStored) {
        val fuelByV = fuelEntries.groupBy { it.vehicleId }
        val expByV = expenses.groupBy { it.vehicleId }
        val ids = (fuelByV.keys + expByV.keys).toSortedSet()
        ids.map { vehicleId ->
            val vFuel = fuelByV[vehicleId].orEmpty()
            val vExp = expByV[vehicleId].orEmpty()
            val allLegsNewestFirst = newestValidLegs(vFuel, defaultStored, maxLegs = Int.MAX_VALUE)
            val legsChrono = allLegsNewestFirst.asReversed() // oldest→newest for avg/last
            VehicleReportStats(
                vehicleId = vehicleId,
                name = vehicleNameById[vehicleId] ?: "Vehicle $vehicleId",
                fuelCostByCurrency = CurrencyCodes.sumByCurrency(
                    vFuel,
                    defaultStored,
                    { it.currency },
                    { it.cost },
                ),
                gallons = vFuel.sumOf { it.gallons },
                fillCount = vFuel.size,
                partialCount = vFuel.count { it.isPartialFill },
                lastMpg = legsChrono.lastOrNull()?.mpg,
                avgMpg = if (legsChrono.isEmpty()) null else legsChrono.map { it.mpg }.average(),
                dollarsPerMile = dollarsPerMile(vFuel, vExp, defaultStored),
                last5Legs = allLegsNewestFirst.take(5),
                expenseTotalByCurrency = CurrencyCodes.sumByCurrency(
                    vExp,
                    defaultStored,
                    { it.currency },
                    { it.amount },
                ),
                expensesByCategory = vExp.groupBy { it.category.ifBlank { "Other" } }
                    .mapValues { (_, list) ->
                        CurrencyCodes.sumByCurrency(
                            list,
                            defaultStored,
                            { it.currency },
                            { it.amount },
                        )
                    }
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
                        totalExpensesByCurrency = totalExpensesByCurrency,
                        totalFuelCostByCurrency = totalFuelCostByCurrency,
                        totalGallons = totalGallons,
                        unitLabel = volumeUnitLabel,
                        totalFillUps = totalFillUps,
                        partialFills = partialFills,
                        defaultSymbol = defaultSymbol,
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
                                        defaultSymbol = defaultSymbol,
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
                                                defaultSymbol = defaultSymbol,
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
                                    defaultSymbol = defaultSymbol,
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
                        defaultSymbol = defaultSymbol,
                        modifier = Modifier.weight(1f)
                    )
                    FillsBlock(
                        fills = allFillsNewest,
                        vehicleNameById = vehicleNameById,
                        volumeUnitLabel = volumeUnitLabel,
                        defaultSymbol = defaultSymbol,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpensesBlock(
                        expenses = allExpensesNewest,
                        defaultSymbol = defaultSymbol,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FillsBlock(
                        fills = allFillsNewest,
                        vehicleNameById = vehicleNameById,
                        volumeUnitLabel = volumeUnitLabel,
                        defaultSymbol = defaultSymbol,
                        modifier = Modifier.fillMaxWidth(),
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
    defaultSymbol: String,
    modifier: Modifier = Modifier
) {
    val statsLine = vehicleStatsOnlyLine(stats, unitLabel, defaultSymbol)
    val expLine = vehicleExpenseSummaryLine(stats, defaultSymbol)
    Column(modifier = modifier) {
        Text(stats.name, style = MaterialTheme.typography.titleSmall)
        AdaptiveStatsText(
            statsLine = statsLine,
            modifier = Modifier.fillMaxWidth()
        )
        AdaptiveStatsText(
            statsLine = expLine,
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
    defaultSymbol: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stats.name, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            VehicleLast5List(
                legs = stats.last5Legs,
                volumeUnitLabel = volumeUnitLabel,
                defaultSymbol = defaultSymbol,
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
    defaultSymbol: String,
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
                    volumeUnitLabel = volumeUnitLabel,
                    defaultSymbol = defaultSymbol,
                )
            }
        }
    }
}

@Composable
private fun FullFillLegRow(
    leg: FullFillLeg,
    barFraction: Float,
    volumeUnitLabel: String,
    defaultSymbol: String,
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
                "${CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, defaultSymbol)} · " +
                    formatVolume(leg.sumVol, volumeUnitLabel),
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
    defaultSymbol: String,
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
                        val head = listOfNotNull(
                            entry.vendor.takeIf { it.isNotBlank() },
                            entry.description.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { "(no description)" }
                        Text(
                            "$head · ${CurrencyCodes.formatAmount(entry.amount, entry.currency, defaultSymbol)}"
                        )
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
    defaultSymbol: String,
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
                            "odo ${entry.odometer} · " +
                                "${CurrencyCodes.formatAmount(entry.cost, entry.currency, defaultSymbol)} · " +
                                formatVolume(entry.gallons, volumeUnitLabel),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
