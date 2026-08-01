package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.trip.TripSegments
import com.davidlang.vehicleexpensesautomated.data.trip.TripTypes
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

private data class TimeMetricToggles(
    val mpg: Boolean = true,
    val gpm: Boolean = false,
    val unitPrice: Boolean = false,
    val dpmFuel: Boolean = true,
    val dpmIncl: Boolean = false,
    val monthlyFuel: Boolean = false,
    val monthlyOther: Boolean = false,
    val tripMiles: Boolean = false,
    val tripPct: Boolean = false,
) {
    val anyOn: Boolean
        get() = mpg || gpm || unitPrice || dpmFuel || dpmIncl ||
            monthlyFuel || monthlyOther || tripMiles || tripPct
}

private data class SmoothPrefs(
    val mode: LabSmoothMode = LabSmoothMode.NONE,
    val customDays: Int = 7,
)

private object TimeChartPrefs {
    private const val PREFS = "vehicle_settings"
    fun loadMetrics(context: Context): TimeMetricToggles {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return TimeMetricToggles(
            mpg = p.getBoolean("reports_lab_time_mpg", true),
            gpm = p.getBoolean("reports_lab_time_gpm", false),
            unitPrice = p.getBoolean("reports_lab_time_unit_price", false),
            dpmFuel = p.getBoolean("reports_lab_time_dpm_fuel", true),
            dpmIncl = p.getBoolean("reports_lab_time_dpm_incl", false),
            monthlyFuel = p.getBoolean("reports_lab_time_monthly_fuel", false),
            monthlyOther = p.getBoolean("reports_lab_time_monthly_other", false),
            tripMiles = p.getBoolean("reports_lab_time_trip_miles", false),
            tripPct = p.getBoolean("reports_lab_time_trip_pct", false),
        )
    }

    fun saveMetrics(context: Context, t: TimeMetricToggles) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("reports_lab_time_mpg", t.mpg)
            .putBoolean("reports_lab_time_gpm", t.gpm)
            .putBoolean("reports_lab_time_unit_price", t.unitPrice)
            .putBoolean("reports_lab_time_dpm_fuel", t.dpmFuel)
            .putBoolean("reports_lab_time_dpm_incl", t.dpmIncl)
            .putBoolean("reports_lab_time_monthly_fuel", t.monthlyFuel)
            .putBoolean("reports_lab_time_monthly_other", t.monthlyOther)
            .putBoolean("reports_lab_time_trip_miles", t.tripMiles)
            .putBoolean("reports_lab_time_trip_pct", t.tripPct)
            .apply()
    }

    fun loadSmooth(context: Context): SmoothPrefs {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modeName = p.getString("reports_lab_time_smooth", LabSmoothMode.NONE.name)
        val mode = runCatching { LabSmoothMode.valueOf(modeName ?: "") }.getOrDefault(LabSmoothMode.NONE)
        return SmoothPrefs(mode = mode, customDays = p.getInt("reports_lab_time_custom_days", 7).coerceAtLeast(1))
    }

    fun saveSmooth(context: Context, s: SmoothPrefs) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("reports_lab_time_smooth", s.mode.name)
            .putInt("reports_lab_time_custom_days", s.customDays.coerceAtLeast(1))
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsLabTimeChartsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val data = rememberLabReportData(LabVehicleMembership.FUEL_OR_EXPENSE)
    var toggles by remember { mutableStateOf(TimeChartPrefs.loadMetrics(context)) }
    var smooth by remember { mutableStateOf(TimeChartPrefs.loadSmooth(context)) }
    var smoothMenu by remember { mutableStateOf(false) }
    var customDaysText by remember { mutableStateOf(smooth.customDays.toString()) }

    fun setToggles(next: TimeMetricToggles) {
        toggles = next
        TimeChartPrefs.saveMetrics(context, next)
    }

    fun setSmooth(next: SmoothPrefs) {
        smooth = next
        customDaysText = next.customDays.toString()
        TimeChartPrefs.saveSmooth(context, next)
    }

    val fillFuel = remember(data.fuel) { data.fuel.withoutTripStarts() }
    val byFuelScope = remember(data.fuel, data.filter.vehicleMode) { data.fuelByVehicleScope() }
    val metricsByScope = remember(byFuelScope, data.expenses, data.defaultStored) {
        byFuelScope.mapValues { (_, fuel) ->
            val legs = allValidLegsChrono(fuel, data.defaultStored)
            val display = excludeMpgOutliers(legs)
            labLegMetrics(display, fuel, data.expenses, data.defaultStored)
        }
    }
    val (periodStart, periodEnd) = remember(data.filter) { periodBounds(data.filter) }
    val tripSegs = remember(data.allFuel, data.filter.vehicleMode, data.filter.vehicleId, periodStart, periodEnd) {
        when (data.filter.vehicleMode) {
            LabVehicleMode.SINGLE -> {
                val vid = data.filter.vehicleId
                if (vid != null) {
                    TripSegments.listSegmentsWithImplicitPersonal(vid, data.allFuel, periodStart, periodEnd)
                } else {
                    TripSegments.listAllSegmentsWithImplicitPersonal(data.allFuel, periodStart, periodEnd)
                }
            }
            else -> TripSegments.listAllSegmentsWithImplicitPersonal(data.allFuel, periodStart, periodEnd)
        }.let { TripSegments.filterForPeriod(it, periodStart, periodEnd) }
    }

    fun prefix(vid: Int?): String =
        if (data.filter.vehicleMode == LabVehicleMode.EACH && vid != null) {
            data.vehicleName(vid) + " "
        } else {
            ""
        }

    val mode = smooth.mode
    val customDays = smooth.customDays

    // --- Series maps ---
    val mpgSeries = remember(metricsByScope, toggles.mpg, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.mpg) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    val legs = metrics.map { it.leg }
                    val pts = economyPointsFromLegsBinned(legs, mode, customDays, asGpm = false)
                    if (pts.isNotEmpty()) {
                        put(prefix(vid) + UnitFormat.economyEfficiencyLabel(), pts)
                    }
                }
            }
        }
    }
    val gpmSeries = remember(metricsByScope, toggles.gpm, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.gpm) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    val legs = metrics.map { it.leg }
                    val pts = economyPointsFromLegsBinned(legs, mode, customDays, asGpm = true)
                    if (pts.isNotEmpty()) put(prefix(vid) + "gpm", pts)
                }
            }
        }
    }
    val unitPriceSeries = remember(byFuelScope, toggles.unitPrice, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.unitPrice) emptyMap()
        else {
            buildMap {
                for ((vid, fuel) in byFuelScope) {
                    val cv = fuel.withoutTripStarts().mapNotNull { e ->
                        if (e.gallons <= 0 || !e.cost.isFinite() || e.cost == 0.0) null
                        else e.timestamp to (e.cost to e.gallons)
                    }
                    val pts = unitPricePointsBinned(cv, mode, customDays)
                    if (pts.isNotEmpty()) put(prefix(vid) + "unit price", pts)
                }
            }
        }
    }
    val dpmFuelSeries = remember(metricsByScope, toggles.dpmFuel, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.dpmFuel) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    val pts = averagePointsByBin(
                        metrics.mapNotNull { m ->
                            m.dpmFuel?.let { LabTimeYPoint(m.leg.endTimestamp, it.toFloat()) }
                        },
                        mode,
                        customDays,
                    )
                    if (pts.isNotEmpty()) {
                        put(prefix(vid) + UnitFormat.costPerDistanceLabel() + " fuel", pts)
                    }
                }
            }
        }
    }
    val dpmInclSeries = remember(metricsByScope, toggles.dpmIncl, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.dpmIncl) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    val pts = averagePointsByBin(
                        metrics.mapNotNull { m ->
                            m.dpmInclExp?.let { LabTimeYPoint(m.leg.endTimestamp, it.toFloat()) }
                        },
                        mode,
                        customDays,
                    )
                    if (pts.isNotEmpty()) {
                        put(prefix(vid) + UnitFormat.costPerDistanceLabel() + " +exp", pts)
                    }
                }
            }
        }
    }
    val chartCurrency = data.defaultStored
    val monthlyFuelSeries = remember(
        fillFuel, data.expenses, toggles.monthlyFuel, mode, customDays, data.filter.vehicleMode, chartCurrency,
    ) {
        if (!toggles.monthlyFuel) emptyMap()
        else monthlyKindSeries(
            fillFuel = fillFuel,
            expenses = data.expenses,
            data = data,
            fuelKind = true,
            chartCurrency = chartCurrency,
            mode = mode,
            customDays = customDays,
        )
    }
    val monthlyOtherSeries = remember(
        fillFuel, data.expenses, toggles.monthlyOther, mode, customDays, data.filter.vehicleMode, chartCurrency,
    ) {
        if (!toggles.monthlyOther) emptyMap()
        else monthlyKindSeries(
            fillFuel = fillFuel,
            expenses = data.expenses,
            data = data,
            fuelKind = false,
            chartCurrency = chartCurrency,
            mode = mode,
            customDays = customDays,
        )
    }
    val tripMilesSeries = remember(tripSegs, toggles.tripMiles, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.tripMiles) emptyMap()
        else {
            val segs = tripSegs.filter { !it.isOpen && !it.isZeroLength }
            when (data.filter.vehicleMode) {
                LabVehicleMode.EACH -> segs.groupBy { it.vehicleId }.mapKeys { (vid, _) ->
                    data.vehicleName(vid) + " trip mi"
                }.mapValues { (_, list) ->
                    sumPointsByBin(
                        list.map { it.startTimestamp to it.miles.toFloat() },
                        mode,
                        customDays,
                    )
                }.filterValues { it.isNotEmpty() }
                else -> mapOf(
                    "trip miles" to sumPointsByBin(
                        segs.map { it.startTimestamp to it.miles.toFloat() },
                        mode,
                        customDays,
                    ),
                ).filterValues { it.isNotEmpty() }
            }
        }
    }
    val tripPctSeries = remember(tripSegs, toggles.tripPct, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.tripPct) emptyMap()
        else {
            fun pctPoints(list: List<TripSegments.Segment>): List<LabTimeYPoint> {
                val closed = list.filter { !it.isOpen && !it.isZeroLength }
                if (closed.isEmpty()) return emptyList()
                if (mode == LabSmoothMode.NONE) {
                    return closed.map { seg ->
                        val trip = if (seg.isPersonal || seg.tripType.equals(TripTypes.PERSONAL, true)) {
                            0f
                        } else {
                            seg.miles.toFloat()
                        }
                        val total = seg.miles.toFloat().coerceAtLeast(1f)
                        LabTimeYPoint(seg.startTimestamp, 100f * trip / total)
                    }
                }
                data class Acc(var trip: Float = 0f, var total: Float = 0f)
                val bins = linkedMapOf<Long, Acc>()
                for (seg in closed) {
                    val k = binKeyMs(seg.startTimestamp, mode, customDays)
                    val a = bins.getOrPut(k) { Acc() }
                    a.total += seg.miles
                    if (!(seg.isPersonal || seg.tripType.equals(TripTypes.PERSONAL, true))) {
                        a.trip += seg.miles
                    }
                }
                return bins.entries.sortedBy { it.key }.map { (k, a) ->
                    val t = a.total.coerceAtLeast(1f)
                    LabTimeYPoint(k, 100f * a.trip / t)
                }
            }
            when (data.filter.vehicleMode) {
                LabVehicleMode.EACH -> tripSegs.groupBy { it.vehicleId }.mapKeys { (vid, _) ->
                    data.vehicleName(vid) + " trip %"
                }.mapValues { (_, list) -> pctPoints(list) }.filterValues { it.isNotEmpty() }
                else -> mapOf("trip %" to pctPoints(tripSegs)).filterValues { it.isNotEmpty() }
            }
        }
    }

    val moneySeries = unitPriceSeries + dpmFuelSeries + dpmInclSeries + monthlyFuelSeries + monthlyOtherSeries
    val tripSeries = tripMilesSeries + tripPctSeries
    val hasEconomy = mpgSeries.isNotEmpty() || gpmSeries.isNotEmpty()
    val hasMoney = moneySeries.isNotEmpty()
    val hasTrip = tripSeries.isNotEmpty()

    val allSeriesForPdf = mpgSeries + gpmSeries + moneySeries + tripSeries

    ReportsLabScreenScaffold(
        title = "Fuel over time",
        infoText = TIME_CHARTS_INFO,
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Fuel over time")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    appendLine("Smooth: ${mode.displayLabel(customDays)}")
                    appendLine(
                        "Metrics: " + listOfNotNull(
                            if (toggles.mpg) "mpg" else null,
                            if (toggles.gpm) "gpm" else null,
                            if (toggles.unitPrice) "unit price" else null,
                            if (toggles.dpmFuel) "\$/mi fuel" else null,
                            if (toggles.dpmIncl) "\$/mi+exp" else null,
                            if (toggles.monthlyFuel) "monthly fuel" else null,
                            if (toggles.monthlyOther) "monthly other" else null,
                            if (toggles.tripMiles) "trip mi" else null,
                            if (toggles.tripPct) "trip %" else null,
                        ).joinToString(", ").ifBlank { "(none)" },
                    )
                    allSeriesForPdf.forEach { (name, pts) ->
                        appendLine("--- $name (${pts.size} pts) ---")
                        pts.sortedBy { it.timestampMs }.forEach { p ->
                            appendLine("${formatLabDate(p.timestampMs)} ${"%.4f".format(p.y)}")
                        }
                    }
                }
            }
            ReportsLabShareActions(
                subject = "Fuel over time",
                textBody = buildText,
                csvFileName = "lab_time_charts.csv",
                csvBody = {
                    val sb = StringBuilder("series,date,value\n")
                    allSeriesForPdf.forEach { (name, pts) ->
                        pts.sortedBy { it.timestampMs }.forEach { p ->
                            sb.append(
                                "${ReportsLabShare.csvEscape(name)},${formatLabDate(p.timestampMs)},${p.y}\n",
                            )
                        }
                    }
                    sb.toString()
                },
                pdfBody = {
                    val sections = mutableListOf(
                        ReportsLabPdf.PdfSection(
                            heading = "Combined series",
                            lines = allSeriesForPdf.keys.toList().ifEmpty { listOf("(none)") },
                        ),
                    )
                    // R4.2: combined summary + per-series sections when multi-series
                    if (allSeriesForPdf.size > 1 || data.filter.vehicleMode == LabVehicleMode.EACH) {
                        allSeriesForPdf.forEach { (name, pts) ->
                            sections += ReportsLabPdf.PdfSection(
                                heading = name,
                                tableRows = listOf(listOf("date", "value")) +
                                    pts.sortedBy { it.timestampMs }.map {
                                        listOf(formatLabDate(it.timestampMs), "%.4f".format(it.y))
                                    },
                            )
                        }
                    } else {
                        allSeriesForPdf.forEach { (name, pts) ->
                            sections += ReportsLabPdf.PdfSection(
                                heading = name,
                                lines = pts.sortedBy { it.timestampMs }.map {
                                    "${formatLabDate(it.timestampMs)}  ${"%.4f".format(it.y)}"
                                },
                            )
                        }
                    }
                    ReportsLabPdf.buildTextReportPdf(
                        title = "Fuel over time",
                        metaLines = listOf(
                            "Period: ${periodLabel(data.filter)}",
                            "Vehicle: ${data.filterVehicleLabel()}",
                            "Smooth: ${mode.displayLabel(customDays)}",
                        ),
                        sections = sections,
                    )
                },
            )
        },
    ) {
        Text("Metrics", style = MaterialTheme.typography.titleSmall)
        MetricRow(UnitFormat.economyEfficiencyLabel(), toggles.mpg) { setToggles(toggles.copy(mpg = it)) }
        MetricRow("gpm (vol / mi)", toggles.gpm) { setToggles(toggles.copy(gpm = it)) }
        MetricRow("Unit price (cost÷vol)", toggles.unitPrice) { setToggles(toggles.copy(unitPrice = it)) }
        MetricRow("${UnitFormat.costPerDistanceLabel()} fuel", toggles.dpmFuel) {
            setToggles(toggles.copy(dpmFuel = it))
        }
        MetricRow("${UnitFormat.costPerDistanceLabel()} +exp", toggles.dpmIncl) {
            setToggles(toggles.copy(dpmIncl = it))
        }
        MetricRow("Monthly fuel \$", toggles.monthlyFuel) { setToggles(toggles.copy(monthlyFuel = it)) }
        MetricRow("Monthly other \$", toggles.monthlyOther) { setToggles(toggles.copy(monthlyOther = it)) }
        MetricRow("Trip miles", toggles.tripMiles) { setToggles(toggles.copy(tripMiles = it)) }
        MetricRow("Trip % (non-personal / total)", toggles.tripPct) { setToggles(toggles.copy(tripPct = it)) }

        Text("Smooth / bin", style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(expanded = smoothMenu, onExpandedChange = { smoothMenu = !smoothMenu }) {
            OutlinedTextField(
                value = mode.displayLabel(customDays),
                onValueChange = {},
                readOnly = true,
                label = { Text("Smooth") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = smoothMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = smoothMenu, onDismissRequest = { smoothMenu = false }) {
                LabSmoothMode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (m == LabSmoothMode.CUSTOM_DAYS) "Manage… (N days)"
                                else m.displayLabel(customDays),
                            )
                        },
                        onClick = {
                            setSmooth(smooth.copy(mode = m))
                            smoothMenu = false
                        },
                    )
                }
            }
        }
        if (mode == LabSmoothMode.CUSTOM_DAYS) {
            OutlinedTextField(
                value = customDaysText,
                onValueChange = { raw ->
                    customDaysText = raw.filter { it.isDigit() }.ifBlank { "" }
                    val n = customDaysText.toIntOrNull()?.coerceAtLeast(1)
                    if (n != null) setSmooth(smooth.copy(customDays = n))
                },
                label = { Text("Custom bin width (days)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Spacer(Modifier.height(8.dp))
        when {
            !toggles.anyOn -> ReportsLabEmpty("No metrics selected")
            !hasEconomy && !hasMoney && !hasTrip -> ReportsLabEmpty("Not enough points for a chart.")
            else -> {
                // Host A: economy left family (mpg Start / gpm End)
                if (hasEconomy) {
                    LabMultiAxisTimeSeriesChart(
                        startSeries = mpgSeries,
                        endSeries = gpmSeries,
                        caption = "Economy (left family) — mpg Start / gpm End · smooth ${mode.displayLabel(customDays)}",
                        emptyMessage = "Not enough economy points.",
                        startAxisLabel = if (mpgSeries.isNotEmpty()) UnitFormat.economyEfficiencyLabel() else null,
                        endAxisLabel = if (gpmSeries.isNotEmpty() && mpgSeries.isNotEmpty()) "gpm" else null,
                        startAxisColor = when {
                            mpgSeries.isNotEmpty() -> LabChartColors.Mpg
                            gpmSeries.isNotEmpty() -> LabChartColors.Gpm
                            else -> null
                        },
                        endAxisColor = if (mpgSeries.isNotEmpty() && gpmSeries.isNotEmpty()) {
                            LabChartColors.Gpm
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // Host B: money / unit price / monthly $ on right family (Start-flip if alone)
                if (hasMoney) {
                    LabMultiAxisTimeSeriesChart(
                        startSeries = emptyMap(),
                        endSeries = moneySeries,
                        caption = "Money (right family) — unit price / \$/mi / monthly",
                        emptyMessage = "Not enough money points.",
                        endAxisLabel = "\$",
                        endAxisColor = LabChartColors.DpmFuel,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // Host C: trip miles / %
                if (hasTrip) {
                    LabMultiAxisTimeSeriesChart(
                        startSeries = emptyMap(),
                        endSeries = tripSeries,
                        caption = "Trip (right family) — miles / %",
                        emptyMessage = "Not enough trip points.",
                        endAxisLabel = "trip",
                        endAxisColor = LabChartColors.DpmIncl,
                    )
                }
            }
        }
    }
}

private fun monthlyKindSeries(
    fillFuel: List<com.davidlang.vehicleexpensesautomated.data.model.FuelEntry>,
    expenses: List<com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry>,
    data: LabReportData,
    fuelKind: Boolean,
    chartCurrency: String,
    mode: LabSmoothMode,
    customDays: Int,
): Map<String, List<LabTimeYPoint>> {
    val scopes = when (data.filter.vehicleMode) {
        LabVehicleMode.EACH -> {
            val f = fillFuel.groupBy { it.vehicleId }
            val e = expenses.groupBy { it.vehicleId }
            (f.keys + e.keys).associateWith { vid ->
                f[vid].orEmpty() to e[vid].orEmpty()
            }
        }
        else -> mapOf(null as Int? to (fillFuel to expenses))
    }
    return buildMap {
        for ((vid, pair) in scopes) {
            val (fuel, exp) = pair
            val buckets = monthlyCostBuckets(fuel, exp, data.defaultStored)
            val contrib = buckets.map { b ->
                // mid-month timestamp for binning
                val ts = try {
                    java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                        .parse(b.key)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
                val amount = if (fuelKind) {
                    (b.fuelByCurrency[chartCurrency] ?: 0.0)
                } else {
                    (b.otherByCurrency[chartCurrency] ?: 0.0)
                }
                ts to amount.toFloat()
            }.filter { it.first > 0 && it.second != 0f }
            val pts = sumPointsByBin(contrib, mode, customDays)
            if (pts.isNotEmpty()) {
                val label = when {
                    data.filter.vehicleMode == LabVehicleMode.EACH && vid != null ->
                        data.vehicleName(vid) + if (fuelKind) " monthly fuel" else " monthly other"
                    fuelKind -> "monthly fuel"
                    else -> "monthly other"
                }
                put(label, pts)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, style = MaterialTheme.typography.bodyMedium, softWrap = true)
    }
}

private const val TIME_CHARTS_INFO =
    "Unified time charts: economy (mpg/gpm), money (unit price, \$/mi, monthly \$), " +
        "and trip miles/%. All metrics optional. " +
        "Axis policy: mpg/gpm on left-family host; \$ and trip metrics never steal left from economy. " +
        "Smooth bins by calendar day/week/month/year or custom N days; None = one point per event. " +
        "Edge-spanning full-fill legs contribute miles/volume to both bins. " +
        "Trip % = non-personal segment miles / total segment miles in bin. " +
        "PDF includes combined series list plus per-series tables when multi-series or Each."
