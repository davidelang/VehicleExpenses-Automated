package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.data.batch.FuelEconomyChains
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lab-local full-fill leg (copied patterns from production Reports; do not edit production). */
data class LabFullFillLeg(
    val endFill: FuelEntry,
    val prevFill: FuelEntry,
    val sumCostByCurrency: Map<String, Double>,
    val sumVol: Double,
    val mpg: Double,
    val miles: Int,
) {
    /** Volume per mile (inverse of mpg) when miles > 0. */
    val gpm: Double?
        get() = if (miles > 0 && sumVol > 0) sumVol / miles.toDouble() else null

    val endTimestamp: Long get() = endFill.timestamp
    val vehicleId: Int get() = endFill.vehicleId
}

/** Time-series chart point (X = capture/leg timestamp). */
data class LabTimeYPoint(
    val timestampMs: Long,
    val y: Float,
    val seriesKey: String = "",
)

private const val DISPLAY_MPG_MIN = 5.0
private const val DISPLAY_MPG_MAX = 80.0

private fun fullFillsAscending(entries: List<FuelEntry>): List<FuelEntry> =
    entries.filter { FuelEconomyChains.isFullFill(it) }
        .sortedWith(compareBy({ it.timestamp }, { it.id }))

/**
 * Valid full-fill legs (oldest→newest). Same rules as production newestValidLegs
 * (chain breakers, odo increase, sumVol > 0).
 */
fun allValidLegsChrono(entries: List<FuelEntry>, defaultStored: String): List<LabFullFillLeg> {
    val full = fullFillsAscending(entries)
    if (full.size < 2) return emptyList()
    val legs = mutableListOf<LabFullFillLeg>()
    for (i in 1 until full.size) {
        val prev = full[i - 1]
        val cur = full[i]
        if (cur.odometer <= prev.odometer) continue
        val between = entries.filter {
            it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
        }
        if (between.any {
                FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.isMpgChainBreaker(it)
            }
        ) {
            continue
        }
        val withVol = between.filter {
            FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.hasVol(it)
        }
        val sumVol = withVol.sumOf { it.gallons }
        if (sumVol <= 0) continue
        val withCost = between.filter {
            FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.hasCost(it)
        }
        val sumCostByCurrency = CurrencyCodes.sumByCurrency(
            withCost,
            defaultStored,
            { it.currency },
            { it.cost },
        )
        val miles = cur.odometer - prev.odometer
        val mpg = miles / sumVol
        legs.add(
            LabFullFillLeg(
                endFill = cur,
                prevFill = prev,
                sumCostByCurrency = sumCostByCurrency,
                sumVol = sumVol,
                mpg = mpg,
                miles = miles,
            ),
        )
    }
    return legs
}

fun excludeMpgOutliers(legs: List<LabFullFillLeg>): List<LabFullFillLeg> {
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

fun lastMpg(legsChrono: List<LabFullFillLeg>): Double? =
    excludeMpgOutliers(legsChrono).lastOrNull()?.mpg

fun avgMpg(legsChrono: List<LabFullFillLeg>): Double? {
    val d = excludeMpgOutliers(legsChrono)
    if (d.isEmpty()) return null
    return d.map { it.mpg }.average()
}

/**
 * Fuel-only $/mi for one leg window (prev→end). Null if miles≤0, no cost, or mixed currency.
 * Uses DPM chain breakers (not MPG breakers).
 */
fun dpmFuelOnly(
    leg: LabFullFillLeg,
    fuelEntries: List<FuelEntry>,
    defaultStored: String,
): Double? {
    if (leg.miles <= 0) return null
    val prev = leg.prevFill
    val cur = leg.endFill
    val between = fuelEntries.filter {
        it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
    }
    if (between.any {
            FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.isDpmChainBreaker(it)
        }
    ) {
        return null
    }
    val withCost = between.filter {
        FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.hasCost(it)
    }
    val byC = CurrencyCodes.sumByCurrency(
        withCost, defaultStored, { it.currency }, { it.cost },
    )
    if (byC.isEmpty() || byC.size != 1) return null
    return byC.values.first() / leg.miles.toDouble()
}

/**
 * Fuel + expenses in leg window ÷ miles. Null if mixed currency or no spend.
 */
fun dpmInclExpenses(
    leg: LabFullFillLeg,
    fuelEntries: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): Double? {
    if (leg.miles <= 0) return null
    val prev = leg.prevFill
    val cur = leg.endFill
    val between = fuelEntries.filter {
        it.timestamp > prev.timestamp && it.timestamp <= cur.timestamp
    }
    if (between.any {
            FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.isDpmChainBreaker(it)
        }
    ) {
        return null
    }
    val combined = mutableMapOf<String, Double>()
    val fuelWithCost = between.filter {
        FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.hasCost(it)
    }
    for ((k, v) in CurrencyCodes.sumByCurrency(
        fuelWithCost, defaultStored, { it.currency }, { it.cost },
    )) {
        combined[k] = combined.getOrDefault(k, 0.0) + v
    }
    val windowExpenses = expenses.filter {
        it.date > prev.timestamp && it.date <= cur.timestamp
    }
    for ((k, v) in CurrencyCodes.sumByCurrency(
        windowExpenses, defaultStored, { it.currency }, { it.amount },
    )) {
        combined[k] = combined.getOrDefault(k, 0.0) + v
    }
    if (combined.isEmpty() || combined.size != 1) return null
    return combined.values.first() / leg.miles.toDouble()
}

/** Legs with per-leg multi-metric values for efficiency charts/export. */
data class LabLegMetrics(
    val leg: LabFullFillLeg,
    val mpg: Double,
    val gpm: Double?,
    val dpmFuel: Double?,
    val dpmInclExp: Double?,
)

fun labLegMetrics(
    legs: List<LabFullFillLeg>,
    fuelEntries: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): List<LabLegMetrics> =
    legs.map { leg ->
        LabLegMetrics(
            leg = leg,
            mpg = leg.mpg,
            gpm = leg.gpm,
            dpmFuel = dpmFuelOnly(leg, fuelEntries, defaultStored),
            dpmInclExp = dpmInclExpenses(leg, fuelEntries, expenses, defaultStored),
        )
    }

fun dollarsPerMile(
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
        if (between.any {
                FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.isDpmChainBreaker(it)
            }
        ) {
            continue
        }
        miles += cur.odometer - prev.odometer
        val fuelWithCost = between.filter {
            FuelEconomyChains.contributesToEconomy(it) && FuelEconomyChains.hasCost(it)
        }
        for ((k, v) in CurrencyCodes.sumByCurrency(
            fuelWithCost, defaultStored, { it.currency }, { it.cost },
        )) {
            combined[k] = combined.getOrDefault(k, 0.0) + v
        }
        val windowExpenses = expenses.filter {
            it.date > prev.timestamp && it.date <= cur.timestamp
        }
        for ((k, v) in CurrencyCodes.sumByCurrency(
            windowExpenses, defaultStored, { it.currency }, { it.amount },
        )) {
            combined[k] = combined.getOrDefault(k, 0.0) + v
        }
    }
    if (miles <= 0) return null
    if (combined.isEmpty() || combined.size != 1) return null
    return combined.values.first() / miles.toDouble()
}

fun formatMpg(value: Double?): String {
    if (value == null) return "n/a"
    if (value < 1.0 || value > 100.0) return "n/a"
    return "%.1f".format(value)
}

fun formatLabDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

fun formatLabDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/** Shared spacing/decimals with [com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.formatVolume]. */
fun formatVolume(gallons: Double, unitLabel: String): String {
    val unit = when (unitLabel.trim().uppercase()) {
        "L", "LITERS" -> com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.LITERS
        else -> com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.GALLONS
    }
    return com.davidlang.vehicleexpensesautomated.ui.util.VolumeUnits.formatVolume(gallons, unit)
}

/** Lab fill-facing sets: exclude open-only trip starts (not fuel inventory fills). */
fun List<FuelEntry>.withoutTripStarts(): List<FuelEntry> =
    FuelEconomyChains.withoutTripStarts(this)

/** Unit price (cost/vol) when both present and vol > 0. */
fun unitPrice(entry: FuelEntry): Double? {
    if (!FuelEconomyChains.hasCost(entry) || !FuelEconomyChains.hasVol(entry)) return null
    if (entry.gallons <= 0) return null
    return entry.cost / entry.gallons
}

data class MonthlyBucket(
    /** yyyy-MM */
    val key: String,
    val fuelByCurrency: Map<String, Double>,
    val otherByCurrency: Map<String, Double>,
)

fun monthlyCostBuckets(
    fuel: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): List<MonthlyBucket> {
    val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
    val fuelBy = fuel.groupBy { fmt.format(Date(it.timestamp)) }
    val expBy = expenses.groupBy { fmt.format(Date(it.date)) }
    val keys = (fuelBy.keys + expBy.keys).sorted()
    return keys.map { key ->
        MonthlyBucket(
            key = key,
            fuelByCurrency = CurrencyCodes.sumByCurrency(
                fuelBy[key].orEmpty(), defaultStored, { it.currency }, { it.cost },
            ),
            otherByCurrency = CurrencyCodes.sumByCurrency(
                expBy[key].orEmpty(), defaultStored, { it.currency }, { it.amount },
            ),
        )
    }
}

fun categoryTotals(
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): Map<String, Map<String, Double>> =
    expenses.groupBy { it.category.ifBlank { "Other" } }
        .mapValues { (_, list) ->
            CurrencyCodes.sumByCurrency(list, defaultStored, { it.currency }, { it.amount })
        }

data class TeaserKpis(
    val fuelCostByCurrency: Map<String, Double>,
    val expenseByCurrency: Map<String, Double>,
    val fillCount: Int,
    val lastMpg: Double?,
    val avgMpg: Double?,
)

fun teaserKpis(
    fuel: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    defaultStored: String,
): TeaserKpis {
    val legs = allValidLegsChrono(fuel, defaultStored)
    val fills = fuel.withoutTripStarts()
    return TeaserKpis(
        fuelCostByCurrency = CurrencyCodes.sumByCurrency(
            fuel, defaultStored, { it.currency }, { it.cost },
        ),
        expenseByCurrency = CurrencyCodes.sumByCurrency(
            expenses, defaultStored, { it.currency }, { it.amount },
        ),
        fillCount = fills.size,
        lastMpg = lastMpg(legs),
        avgMpg = avgMpg(legs),
    )
}

/** Hub / summary card metrics (mirrors production Reports overall + per-vehicle lines). */
data class HubVehicleSummary(
    val vehicleId: Int,
    val name: String,
    val fuelCostByCurrency: Map<String, Double>,
    val gallons: Double,
    val fillCount: Int,
    val partialCount: Int,
    val lastMpg: Double?,
    val avgMpg: Double?,
    val dollarsPerMile: Double?,
    val expenseTotalByCurrency: Map<String, Double>,
)

fun hubVehicleSummaries(
    fuel: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    nameById: Map<Int, String>,
    defaultStored: String,
): List<HubVehicleSummary> {
    val fuelByV = fuel.groupBy { it.vehicleId }
    val expByV = expenses.groupBy { it.vehicleId }
    val ids = (fuelByV.keys + expByV.keys).toSortedSet()
    return ids.map { vehicleId ->
        val vFuel = fuelByV[vehicleId].orEmpty()
        val vFills = vFuel.withoutTripStarts()
        val vExp = expByV[vehicleId].orEmpty()
        val legs = allValidLegsChrono(vFuel, defaultStored)
        HubVehicleSummary(
            vehicleId = vehicleId,
            name = when {
                vehicleId == 0 -> "Unknown"
                else -> nameById[vehicleId] ?: "Vehicle $vehicleId"
            },
            fuelCostByCurrency = CurrencyCodes.sumByCurrency(
                vFuel, defaultStored, { it.currency }, { it.cost },
            ),
            gallons = vFuel.sumOf { it.gallons },
            fillCount = vFills.size,
            partialCount = vFills.count { it.isPartialFill },
            lastMpg = lastMpg(legs),
            avgMpg = avgMpg(legs),
            dollarsPerMile = dollarsPerMile(vFuel, vExp, defaultStored),
            expenseTotalByCurrency = CurrencyCodes.sumByCurrency(
                vExp, defaultStored, { it.currency }, { it.amount },
            ),
        )
    }
}

fun hubOverallSummaryLine(
    fuel: List<FuelEntry>,
    expenses: List<ExpenseEntry>,
    volumeLabel: String,
    defaultSymbol: String,
    defaultStored: String,
): String {
    val fills = fuel.withoutTripStarts()
    val exp = CurrencyCodes.formatAggregateSum(
        CurrencyCodes.sumByCurrency(expenses, defaultStored, { it.currency }, { it.amount }),
        defaultSymbol,
    )
    val fuelSum = CurrencyCodes.formatAggregateSum(
        CurrencyCodes.sumByCurrency(fuel, defaultStored, { it.currency }, { it.cost }),
        defaultSymbol,
    )
    val vol = formatVolume(fuel.sumOf { it.gallons }, volumeLabel)
    val partial = fills.count { it.isPartialFill }
    return "Exp $exp · Fuel $fuelSum · $vol · fills ${fills.size} (${partial}p)"
}

fun hubVehicleStatsLine(
    stats: HubVehicleSummary,
    volumeLabel: String,
    defaultSymbol: String,
    costPerDistanceLabel: String = com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.costPerDistanceLabel(),
): String {
    val dpm = if (stats.dollarsPerMile == null) {
        "n/a"
    } else {
        "%.3f".format(stats.dollarsPerMile)
    }
    val fuel = CurrencyCodes.formatAggregateSum(stats.fuelCostByCurrency, defaultSymbol)
    return "Fuel $fuel · " +
        "${formatVolume(stats.gallons, volumeLabel)} · " +
        "fills ${stats.fillCount}(${stats.partialCount}p) · " +
        "last ${formatMpg(stats.lastMpg)} · avg ${formatMpg(stats.avgMpg)} · " +
        "$costPerDistanceLabel $dpm"
}

fun hubVehicleExpenseLine(stats: HubVehicleSummary, defaultSymbol: String): String {
    val total = CurrencyCodes.formatAggregateSum(stats.expenseTotalByCurrency, defaultSymbol)
    return "Exp $total"
}

/**
 * Odometer range for summary: any odo-bearing non-deleted row (fills **and** trip starts).
 * Fill **counts** must use [withoutTripStarts] separately.
 */
fun odometerRange(fuel: List<FuelEntry>): Pair<Int?, Int?> {
    val withOdo = fuel.filter { !it.deleted && FuelEconomyChains.hasOdo(it) }
    if (withOdo.isEmpty()) return null to null
    return withOdo.minOf { it.odometer } to withOdo.maxOf { it.odometer }
}

fun monthLabel(key: String): String = key
