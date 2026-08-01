package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Smooth / bin filter for the unified time report (R3). */
enum class LabSmoothMode {
    NONE,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    CUSTOM_DAYS,
}

fun LabSmoothMode.displayLabel(customDays: Int): String = when (this) {
    LabSmoothMode.NONE -> "None"
    LabSmoothMode.DAY -> "Day"
    LabSmoothMode.WEEK -> "Week"
    LabSmoothMode.MONTH -> "Month"
    LabSmoothMode.YEAR -> "Year"
    LabSmoothMode.CUSTOM_DAYS -> "Every $customDays d"
}

/**
 * Canonical bin start timestamp (ms) for [timestampMs] under [mode].
 * [NONE] returns the timestamp unchanged (one point per event).
 */
fun binKeyMs(timestampMs: Long, mode: LabSmoothMode, customDays: Int): Long {
    if (mode == LabSmoothMode.NONE) return timestampMs
    val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
    when (mode) {
        LabSmoothMode.DAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        LabSmoothMode.WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        LabSmoothMode.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        LabSmoothMode.YEAR -> {
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        LabSmoothMode.CUSTOM_DAYS -> {
            val days = customDays.coerceAtLeast(1)
            val dayMs = TimeUnit.DAYS.toMillis(days.toLong())
            val epochDay = timestampMs / dayMs
            return epochDay * dayMs
        }
        LabSmoothMode.NONE -> Unit
    }
    return cal.timeInMillis
}

/** Inclusive bin end (exclusive next start minus 1ms for calendar modes). */
fun binEndMs(binStart: Long, mode: LabSmoothMode, customDays: Int): Long {
    if (mode == LabSmoothMode.NONE) return binStart
    val cal = Calendar.getInstance().apply { timeInMillis = binStart }
    when (mode) {
        LabSmoothMode.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        LabSmoothMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        LabSmoothMode.MONTH -> cal.add(Calendar.MONTH, 1)
        LabSmoothMode.YEAR -> cal.add(Calendar.YEAR, 1)
        LabSmoothMode.CUSTOM_DAYS -> {
            val days = customDays.coerceAtLeast(1)
            return binStart + TimeUnit.DAYS.toMillis(days.toLong()) - 1
        }
        LabSmoothMode.NONE -> return binStart
    }
    return cal.timeInMillis - 1
}

/**
 * Bin keys that overlap half-open event window [windowStart, windowEnd] (ms).
 * Edge-spanning legs contribute to **both** bins (R3.5 full contribute).
 */
fun overlappingBinKeys(
    windowStart: Long,
    windowEnd: Long,
    mode: LabSmoothMode,
    customDays: Int,
): List<Long> {
    if (mode == LabSmoothMode.NONE) {
        // Attribute to end of window (leg end / event time).
        return listOf(windowEnd)
    }
    val start = minOf(windowStart, windowEnd)
    val end = maxOf(windowStart, windowEnd)
    val keys = linkedSetOf<Long>()
    var t = binKeyMs(start, mode, customDays)
    val last = binKeyMs(end, mode, customDays)
    var guard = 0
    while (t <= last && guard < 10_000) {
        keys += t
        val next = binEndMs(t, mode, customDays) + 1
        if (next <= t) break
        t = binKeyMs(next, mode, customDays)
        guard++
    }
    if (keys.isEmpty()) keys += last
    return keys.toList()
}

fun formatBinLabel(binStartMs: Long, mode: LabSmoothMode): String {
    val fmt = when (mode) {
        LabSmoothMode.NONE -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        LabSmoothMode.DAY, LabSmoothMode.CUSTOM_DAYS ->
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        LabSmoothMode.WEEK -> java.text.SimpleDateFormat("'W'ww yyyy", Locale.US)
        LabSmoothMode.MONTH -> java.text.SimpleDateFormat("yyyy-MM", Locale.US)
        LabSmoothMode.YEAR -> java.text.SimpleDateFormat("yyyy", Locale.US)
    }
    return fmt.format(java.util.Date(binStartMs))
}

/** Aggregate time-series points into bin-mean points (one Y per bin key). */
fun averagePointsByBin(
    points: List<LabTimeYPoint>,
    mode: LabSmoothMode,
    customDays: Int,
): List<LabTimeYPoint> {
    if (mode == LabSmoothMode.NONE || points.isEmpty()) return points.sortedBy { it.timestampMs }
    val groups = linkedMapOf<Long, MutableList<Float>>()
    for (p in points) {
        val k = binKeyMs(p.timestampMs, mode, customDays)
        groups.getOrPut(k) { mutableListOf() }.add(p.y)
    }
    return groups.entries.sortedBy { it.key }.map { (k, ys) ->
        LabTimeYPoint(k, ys.average().toFloat(), points.firstOrNull()?.seriesKey.orEmpty())
    }
}

/**
 * Sum miles/vol into bins that overlap each leg window; return mpg (or gpm) points.
 * [asGpm] true → y = vol/miles; false → miles/vol.
 */
fun economyPointsFromLegsBinned(
    legs: List<LabFullFillLeg>,
    mode: LabSmoothMode,
    customDays: Int,
    asGpm: Boolean,
): List<LabTimeYPoint> {
    if (legs.isEmpty()) return emptyList()
    if (mode == LabSmoothMode.NONE) {
        return legs.mapNotNull { leg ->
            val y = if (asGpm) {
                leg.gpm?.toFloat()
            } else {
                leg.mpg.toFloat()
            } ?: return@mapNotNull null
            LabTimeYPoint(leg.endTimestamp, y)
        }
    }
    data class Acc(var miles: Double = 0.0, var vol: Double = 0.0)
    val bins = linkedMapOf<Long, Acc>()
    for (leg in legs) {
        val wStart = leg.prevFill.timestamp
        val wEnd = leg.endFill.timestamp
        for (key in overlappingBinKeys(wStart, wEnd, mode, customDays)) {
            val a = bins.getOrPut(key) { Acc() }
            a.miles += leg.miles.toDouble()
            a.vol += leg.sumVol
        }
    }
    return bins.entries.sortedBy { it.key }.mapNotNull { (k, a) ->
        if (a.miles <= 0 || a.vol <= 0) return@mapNotNull null
        val y = if (asGpm) (a.vol / a.miles).toFloat() else (a.miles / a.vol).toFloat()
        LabTimeYPoint(k, y)
    }
}

/** Sum numeric contributions per bin (costs, miles, etc.). */
fun sumPointsByBin(
    contributions: List<Pair<Long, Float>>,
    mode: LabSmoothMode,
    customDays: Int,
): List<LabTimeYPoint> {
    if (contributions.isEmpty()) return emptyList()
    if (mode == LabSmoothMode.NONE) {
        return contributions.sortedBy { it.first }.map { LabTimeYPoint(it.first, it.second) }
    }
    val bins = linkedMapOf<Long, Float>()
    for ((ts, y) in contributions) {
        val k = binKeyMs(ts, mode, customDays)
        bins[k] = (bins[k] ?: 0f) + y
    }
    return bins.entries.sortedBy { it.key }.map { LabTimeYPoint(it.key, it.value) }
}

/** Unit price per bin = Σcost / Σvol for fills in that bin. */
fun unitPricePointsBinned(
    fills: List<Pair<Long, Pair<Double, Double>>>, // ts to (cost, vol)
    mode: LabSmoothMode,
    customDays: Int,
): List<LabTimeYPoint> {
    if (fills.isEmpty()) return emptyList()
    if (mode == LabSmoothMode.NONE) {
        return fills.mapNotNull { (ts, cv) ->
            val (c, v) = cv
            if (v <= 0) null else LabTimeYPoint(ts, (c / v).toFloat())
        }
    }
    data class Acc(var cost: Double = 0.0, var vol: Double = 0.0)
    val bins = linkedMapOf<Long, Acc>()
    for ((ts, cv) in fills) {
        val k = binKeyMs(ts, mode, customDays)
        val a = bins.getOrPut(k) { Acc() }
        a.cost += cv.first
        a.vol += cv.second
    }
    return bins.entries.sortedBy { it.key }.mapNotNull { (k, a) ->
        if (a.vol <= 0) null else LabTimeYPoint(k, (a.cost / a.vol).toFloat())
    }
}

/**
 * Trip metrics from odo timeline: total trip miles + **per-type** trip % (P1–P7).
 * Walk odo-bearing fills; assign each Δodo to the open trip type at the later event
 * (Personal included). Types with any non-zero miles in the window get a series.
 */
data class TripOdoMetrics(
    val milesTotal: List<LabTimeYPoint>,
    /** Type name → % points (0–100) on the same Smooth grid. */
    val pctByType: Map<String, List<LabTimeYPoint>>,
)

fun tripMetricsFromOdo(
    fuel: List<com.davidlang.vehicleexpensesautomated.data.model.FuelEntry>,
    mode: LabSmoothMode,
    customDays: Int,
): TripOdoMetrics {
    val events = fuel
        .filter { !it.deleted && it.odometer > 0 }
        .sortedWith(compareBy({ it.timestamp }, { it.id }))
    if (events.size < 2) return TripOdoMetrics(emptyList(), emptyMap())

    var openType: String? = null
    val typeAtIndex = ArrayList<String?>(events.size)
    for (e in events) {
        if (e.tripType.isNotBlank()) {
            openType = e.tripType
        }
        typeAtIndex.add(openType)
    }

    // binKey -> (total miles, miles by type)
    data class BinAcc(
        var total: Float = 0f,
        val byType: MutableMap<String, Float> = linkedMapOf(),
    )
    val bins = linkedMapOf<Long, BinAcc>()
    val windowTypeMiles = linkedMapOf<String, Float>()

    for (i in 1 until events.size) {
        val prev = events[i - 1]
        val cur = events[i]
        val delta = cur.odometer - prev.odometer
        if (delta <= 0) continue
        val type = typeAtIndex[i]
        val ts = cur.timestamp
        val key = if (mode == LabSmoothMode.NONE) ts else binKeyMs(ts, mode, customDays)
        val a = bins.getOrPut(key) { BinAcc() }
        a.total += delta
        if (type != null) {
            a.byType[type] = (a.byType[type] ?: 0f) + delta
            windowTypeMiles[type] = (windowTypeMiles[type] ?: 0f) + delta
        }
    }

    val milesTotal = bins.entries.sortedBy { it.key }.mapNotNull { (k, a) ->
        val tripSum = a.byType.values.sum()
        if (tripSum <= 0f && a.total <= 0f) null
        else LabTimeYPoint(k, tripSum)
    }

    // P2: only types with any non-zero miles in the filtered window
    val types = windowTypeMiles.filter { it.value > 0f }.keys.sorted()
    val pctByType = types.associateWith { typeName ->
        bins.entries.sortedBy { it.key }.mapNotNull { (k, a) ->
            if (a.total <= 0f) null
            else {
                val m = a.byType[typeName] ?: 0f
                LabTimeYPoint(k, 100f * m / a.total)
            }
        }
    }.filterValues { it.isNotEmpty() }

    return TripOdoMetrics(milesTotal = milesTotal, pctByType = pctByType)
}

/** @deprecated Prefer [tripMetricsFromOdo]; aggregate any-trip % kept for callers. */
fun tripMilesAndPctFromOdo(
    fuel: List<com.davidlang.vehicleexpensesautomated.data.model.FuelEntry>,
    mode: LabSmoothMode,
    customDays: Int,
): Pair<List<LabTimeYPoint>, List<LabTimeYPoint>> {
    val m = tripMetricsFromOdo(fuel, mode, customDays)
    // Aggregate % = sum of type % ≈ 100 when all miles under a type
    val aggPct = m.milesTotal.map { pt ->
        val typesAt = m.pctByType.values.mapNotNull { series ->
            series.firstOrNull { it.timestampMs == pt.timestampMs }?.y
        }
        LabTimeYPoint(pt.timestampMs, typesAt.sum().coerceIn(0f, 100f))
    }
    return m.milesTotal to aggPct
}
