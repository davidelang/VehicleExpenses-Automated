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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.batch.FuelEconomyChains
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.expenses.ExpenseViewModel
import com.davidlang.vehicleexpensesautomated.ui.fuel.FuelViewModel
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.EmptyStateText
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits
import com.davidlang.vehicleexpensesautomated.ui.vehicle.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Math helpers (field-conditional full fills + MPG / $/mi chains) ---
// Shared with Stage C via FuelEconomyChains (REPORTS_METRICS).
private fun hasOdo(e: FuelEntry): Boolean = FuelEconomyChains.hasOdo(e)
private fun hasCost(e: FuelEntry): Boolean = FuelEconomyChains.hasCost(e)
private fun hasVol(e: FuelEntry): Boolean = FuelEconomyChains.hasVol(e)
private fun isFullFill(e: FuelEntry): Boolean = FuelEconomyChains.isFullFill(e)
private fun contributesToEconomy(e: FuelEntry): Boolean = FuelEconomyChains.contributesToEconomy(e)
private fun isMpgChainBreaker(e: FuelEntry): Boolean = FuelEconomyChains.isMpgChainBreaker(e)
private fun isDpmChainBreaker(e: FuelEntry): Boolean = FuelEconomyChains.isDpmChainBreaker(e)

/** Full fill points (time-sorted ascending; id tie-break). */
private fun fullFillsAscending(entries: List<FuelEntry>): List<FuelEntry> {
    return entries
        .filter { isFullFill(it) }
        .sortedWith(compareBy({ it.timestamp }, { it.id }))
}

/**
 * One full-fill leg ending at [endFill] (must have a previous full).
 * cost/vol are rolled sums over (prev.ts, end.ts] for rows with cost/vol present.
 */
private data class FullFillLeg(
    val endFill: FuelEntry,
    val sumCostByCurrency: Map<String, Double>,
    val sumVol: Double,
    val mpg: Double
)

/**
 * Newest valid full-fill legs (newest first). Excludes first full (no predecessor).
 * Skips pairs with any MPG chain breaker in (prev.ts, cur.ts].
 * Only legs with odo increase and sumVol > 0 — every leg has defined mpg.
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
        val between = entries.filter {
            it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
        }
        if (between.any { contributesToEconomy(it) && isMpgChainBreaker(it) }) continue
        val withVol = between.filter { contributesToEconomy(it) && hasVol(it) }
        val sumVol = withVol.sumOf { it.gallons }
        if (sumVol <= 0) continue
        val withCost = between.filter { contributesToEconomy(it) && hasCost(it) }
        val sumCostByCurrency = CurrencyCodes.sumByCurrency(
            withCost,
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
 * **$/mi** over unbroken full→full segments only.
 *
 * For each adjacent full-fill pair with odo increase and no $/mi chain breaker
 * in (prev.ts, cur.ts]: add miles and fuel costs (hasCost) + expenses whose
 * date falls in that window. Odo-only rows never set endpoints or break.
 *
 * @return n/a (null) if no segment miles, empty cost map, or mixed currency.
 */
private fun dollarsPerMile(
    fuelEntries: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): Double? {
    val full = fullFillsAscending(fuelEntries)
    if (full.size < 2) return null
    var miles = 0
    val combined = mutableMapOf<String, Double>()
    for (i in 1 until full.size) {
        val prev = full[i - 1]
        val cur = full[i]
        if (cur.odometer <= prev.odometer) continue
        val between = fuelEntries.filter {
            it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
        }
        if (between.any { contributesToEconomy(it) && isDpmChainBreaker(it) }) continue
        miles += cur.odometer - prev.odometer
        val fuelWithCost = between.filter { contributesToEconomy(it) && hasCost(it) }
        val fuelSums = CurrencyCodes.sumByCurrency(
            fuelWithCost,
            defaultStored,
            { it.currency },
            { it.cost },
        )
        for ((k, v) in fuelSums) combined[k] = combined.getOrDefault(k, 0.0) + v
        val windowExpenses = expenses.filter {
            it.date > prev.timestamp && it.date <= cur.timestamp
        }
        val expSums = CurrencyCodes.sumByCurrency(
            windowExpenses,
            defaultStored,
            { it.currency },
            { it.amount },
        )
        for ((k, v) in expSums) combined[k] = combined.getOrDefault(k, 0.0) + v
    }
    if (miles <= 0) return null
    if (combined.isEmpty() || combined.size != 1) return null
    val totalCost = combined.values.first()
    return totalCost / miles.toDouble()
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

/** Absolute display band for leg mpg (filter only — no row mutation). */
private const val DISPLAY_MPG_MIN = 5.0
private const val DISPLAY_MPG_MAX = 80.0

/**
 * Display filter: keep mpg in 5–80, then drop 3× median outliers.
 * Does not mutate fuel rows.
 */
private fun excludeMpgOutliers(legs: List<FullFillLeg>): List<FullFillLeg> {
    val banded = legs.filter { it.mpg in DISPLAY_MPG_MIN..DISPLAY_MPG_MAX }
    if (banded.size < 3) return banded
    val sorted = banded.map { it.mpg }.sorted()
    val mid = sorted.size / 2
    val ref = if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
    if (ref <= 0) return banded
    return banded.filter { it.mpg >= ref / 3.0 && it.mpg <= ref * 3.0 }
}

private fun formatMpg(value: Double?): String {
    if (value == null) return "n/a"
    if (value < 1.0 || value > 100.0) return "n/a"
    return "%.1f".format(value)
}

private fun formatEntryDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatVolume(gallons: Double, unitLabel: String): String {
    val unit = when (unitLabel.trim().uppercase()) {
        "L", "LITERS" -> VolumeUnits.LITERS
        else -> VolumeUnits.GALLONS
    }
    return VolumeUnits.formatVolume(gallons, unit)
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
        "${formatVolume(totalGallons, unitLabel)} · fills $totalFillUps (${partialFills}p)"
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
        "${formatVolume(stats.gallons, unitLabel)} · " +
        "fills ${stats.fillCount}(${stats.partialCount}p) · " +
        "last ${formatMpg(stats.lastMpg)} · avg ${formatMpg(stats.avgMpg)} · " +
        "${UnitFormat.costPerDistanceLabel()} $dpm"
}

/** User-facing vehicle label: never “Vehicle 0”. */
fun reportVehicleDisplayName(vehicleId: Int, nameById: Map<Int, String>): String {
    if (vehicleId == 0) return "Unknown"
    return nameById[vehicleId] ?: "Vehicle $vehicleId"
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
    // Inventory fills exclude trip starts; $ / volume still from all non-deleted fuel rows.
    val fillInventory = remember(fuelEntries) { FuelEconomyChains.withoutTripStarts(fuelEntries) }
    val totalGallons = fuelEntries.sumOf { it.gallons }
    val partialFills = fillInventory.count { it.isPartialFill }
    val totalFillUps = fillInventory.size

    val vehicleStats = remember(fuelEntries, expenses, vehicleNameById, defaultStored) {
        val fuelByV = fuelEntries.groupBy { it.vehicleId }
        val expByV = expenses.groupBy { it.vehicleId }
        val ids = (fuelByV.keys + expByV.keys).toSortedSet()
        ids.map { vehicleId ->
            val vFuel = fuelByV[vehicleId].orEmpty()
            val vFills = FuelEconomyChains.withoutTripStarts(vFuel)
            val vExp = expByV[vehicleId].orEmpty()
            val allLegsNewestFirst = newestValidLegs(vFuel, defaultStored, maxLegs = Int.MAX_VALUE)
            val legsChrono = allLegsNewestFirst.asReversed() // oldest→newest for avg/last
            // Display avg excludes 3× MPG outliers (same product rule as pending detect)
            val displayLegs = excludeMpgOutliers(legsChrono)
            VehicleReportStats(
                vehicleId = vehicleId,
                name = reportVehicleDisplayName(vehicleId, vehicleNameById),
                fuelCostByCurrency = CurrencyCodes.sumByCurrency(
                    vFuel,
                    defaultStored,
                    { it.currency },
                    { it.cost },
                ),
                gallons = vFuel.sumOf { it.gallons },
                fillCount = vFills.size,
                partialCount = vFills.count { it.isPartialFill },
                lastMpg = displayLegs.lastOrNull()?.mpg,
                avgMpg = if (displayLegs.isEmpty()) null else displayLegs.map { it.mpg }.average(),
                dollarsPerMile = dollarsPerMile(vFuel, vExp, defaultStored),
                last5Legs = excludeMpgOutliers(allLegsNewestFirst).take(5),
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

    val allFillsNewest = remember(fillInventory) {
        fillInventory.sortedByDescending { it.timestamp }.let { if (it.size > 50) it.take(50) else it }
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
        Text(
            "Summary, last full fills, expenses, and fill history per vehicle. Mixed currencies show separate totals (no conversion).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    AdaptiveItemGrid(items = vehicleStats) { stats ->
                        // No fillMaxWidth on grid item root — measure wrap for multi-col
                        VehicleSummaryBlock(
                            stats = stats,
                            unitLabel = volumeUnitLabel,
                            defaultSymbol = defaultSymbol,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Last 5 full fills", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (vehicleStats.isEmpty()) {
            EmptyStateText("No vehicles with data")
        } else {
            AdaptiveItemGrid(items = vehicleStats) { stats ->
                VehicleLast5OnlyColumn(
                    stats = stats,
                    volumeUnitLabel = volumeUnitLabel,
                    defaultSymbol = defaultSymbol,
                    modifier = Modifier,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 320.dp
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
    // Wrap content width so AdaptiveItemGrid natural measure is not forced full-row.
    Column(modifier = modifier.wrapContentWidth(align = Alignment.Start)) {
        Text(stats.name, style = MaterialTheme.typography.titleSmall)
        AdaptiveStatsText(statsLine = statsLine, modifier = Modifier)
        AdaptiveStatsText(statsLine = expLine, modifier = Modifier)
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
        // Infinite max during AdaptiveItemGrid natural measure — wrap to text width.
        val bounded = constraints.hasBoundedWidth && constraints.maxWidth < Constraints.Infinity
        if (!bounded || maxWidth <= 0.dp) {
            Text(statsLine, style = style)
            return@BoxWithConstraints
        }
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
    Card(modifier = modifier.wrapContentWidth(align = Alignment.Start)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stats.name, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            VehicleLast5List(
                legs = stats.last5Legs,
                volumeUnitLabel = volumeUnitLabel,
                defaultSymbol = defaultSymbol,
                modifier = Modifier
                    .heightIn(max = vehicleColMaxHeight)
                    .verticalScroll(rememberScrollState()),
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
                    .height(22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                )
                Text(
                    "${UnitFormat.economyEfficiencyLabel()} ${formatMpg(leg.mpg)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
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
                val name = reportVehicleDisplayName(entry.vehicleId, vehicleNameById)
                val flags = buildList {
                    if (entry.isPartialFill) add("partial")
                    if (entry.economyIgnored) add("ignored")
                }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("$name · ${formatEntryDate(entry.timestamp)}$flags")
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
