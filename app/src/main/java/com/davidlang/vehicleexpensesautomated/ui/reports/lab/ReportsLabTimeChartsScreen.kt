package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
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

private data class MetricDef(
    val key: String,
    val label: String,
    val color: Color,
    val checked: Boolean,
    val onChecked: (Boolean) -> Unit,
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

    val mpgLabel = UnitFormat.economyEfficiencyLabel()
    val gpmLabel = UnitFormat.volumePerDistanceLabel(context)
    val unitPriceLabel = UnitFormat.unitPriceLabel(context)
    val dpmLabel = UnitFormat.costPerDistanceLabel()

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

    fun prefix(vid: Int?): String =
        if (data.filter.vehicleMode == LabVehicleMode.EACH && vid != null) {
            data.vehicleName(vid) + " "
        } else {
            ""
        }

    val mode = smooth.mode
    val customDays = smooth.customDays

    val mpgSeries = remember(metricsByScope, toggles.mpg, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.mpg) emptyMap()
        else buildMap {
            for ((vid, metrics) in metricsByScope) {
                val pts = economyPointsFromLegsBinned(metrics.map { it.leg }, mode, customDays, asGpm = false)
                if (pts.isNotEmpty()) put(prefix(vid) + mpgLabel, pts)
            }
        }
    }
    val gpmSeries = remember(metricsByScope, toggles.gpm, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.gpm) emptyMap()
        else buildMap {
            for ((vid, metrics) in metricsByScope) {
                val pts = economyPointsFromLegsBinned(metrics.map { it.leg }, mode, customDays, asGpm = true)
                if (pts.isNotEmpty()) put(prefix(vid) + gpmLabel, pts)
            }
        }
    }
    val unitPriceSeries = remember(byFuelScope, toggles.unitPrice, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.unitPrice) emptyMap()
        else buildMap {
            for ((vid, fuel) in byFuelScope) {
                val cv = fuel.withoutTripStarts().mapNotNull { e ->
                    if (e.gallons <= 0 || e.cost == 0.0) null
                    else e.timestamp to (e.cost to e.gallons)
                }
                val pts = unitPricePointsBinned(cv, mode, customDays)
                if (pts.isNotEmpty()) put(prefix(vid) + unitPriceLabel, pts)
            }
        }
    }
    val dpmFuelSeries = remember(metricsByScope, toggles.dpmFuel, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.dpmFuel) emptyMap()
        else buildMap {
            for ((vid, metrics) in metricsByScope) {
                val pts = averagePointsByBin(
                    metrics.mapNotNull { m ->
                        m.dpmFuel?.let { LabTimeYPoint(m.leg.endTimestamp, it.toFloat()) }
                    },
                    mode,
                    customDays,
                )
                if (pts.isNotEmpty()) put(prefix(vid) + "$dpmLabel fuel", pts)
            }
        }
    }
    val dpmInclSeries = remember(metricsByScope, toggles.dpmIncl, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.dpmIncl) emptyMap()
        else buildMap {
            for ((vid, metrics) in metricsByScope) {
                val pts = averagePointsByBin(
                    metrics.mapNotNull { m ->
                        m.dpmInclExp?.let { LabTimeYPoint(m.leg.endTimestamp, it.toFloat()) }
                    },
                    mode,
                    customDays,
                )
                if (pts.isNotEmpty()) put(prefix(vid) + "$dpmLabel +exp", pts)
            }
        }
    }
    val chartCurrency = data.defaultStored
    val monthlyFuelSeries = remember(
        fillFuel, data.expenses, toggles.monthlyFuel, mode, customDays, data.filter.vehicleMode, chartCurrency,
    ) {
        if (!toggles.monthlyFuel) emptyMap()
        else monthlyKindSeries(fillFuel, data.expenses, data, true, chartCurrency, mode, customDays, "Fuel $")
    }
    val monthlyOtherSeries = remember(
        fillFuel, data.expenses, toggles.monthlyOther, mode, customDays, data.filter.vehicleMode, chartCurrency,
    ) {
        if (!toggles.monthlyOther) emptyMap()
        else monthlyKindSeries(fillFuel, data.expenses, data, false, chartCurrency, mode, customDays, "Other $")
    }

    // Trip miles/% from odo timeline (includes Personal) — same Smooth grid
    val tripMilesSeries = remember(byFuelScope, toggles.tripMiles, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.tripMiles) emptyMap()
        else buildMap {
            for ((vid, fuel) in byFuelScope) {
                val (milesPts, _) = tripMilesAndPctFromOdo(fuel, mode, customDays)
                if (milesPts.isNotEmpty()) put(prefix(vid) + "Trip miles", milesPts)
            }
        }
    }
    val tripPctSeries = remember(byFuelScope, toggles.tripPct, mode, customDays, data.filter.vehicleMode) {
        if (!toggles.tripPct) emptyMap()
        else buildMap {
            for ((vid, fuel) in byFuelScope) {
                val (_, pctPts) = tripMilesAndPctFromOdo(fuel, mode, customDays)
                if (pctPts.isNotEmpty()) put(prefix(vid) + "Trip %", pctPts)
            }
        }
    }

    val moneySeries = unitPriceSeries + dpmFuelSeries + dpmInclSeries + monthlyFuelSeries + monthlyOtherSeries
    val tripSeries = tripMilesSeries + tripPctSeries
    val hasMoneyOrTrip = moneySeries.isNotEmpty() || tripSeries.isNotEmpty()
    val hasEconomy = mpgSeries.isNotEmpty() || gpmSeries.isNotEmpty()

    // Fixed sides (A1–A4): economy always left; money/trip always right. Never put gpm on End.
    val startSeries = mpgSeries + gpmSeries
    val endSeries = moneySeries + tripSeries
    val startColor = when {
        mpgSeries.isNotEmpty() -> LabChartColors.Mpg
        gpmSeries.isNotEmpty() -> LabChartColors.Gpm
        else -> null
    }
    val endColor = if (hasMoneyOrTrip) LabChartColors.DpmFuel else null
    val caption = "Time based reports · economy left · \$/trip right · smooth ${mode.displayLabel(customDays)}"

    val allSeriesForPdf = startSeries + endSeries
    val seriesColorMap = remember(allSeriesForPdf.keys) {
        allSeriesForPdf.keys.associateWith { key ->
            familyColorForSeriesKey(key)
                ?: when {
                    key.contains("Trip %", ignoreCase = true) -> LabChartColors.DpmIncl
                    key.contains("Trip miles", ignoreCase = true) -> Color(0xFFEF6C00)
                    key.contains("Fuel $") -> LabChartColors.DpmFuel
                    key.contains("Other $") -> Color(0xFF5D4037)
                    else -> LabChartColors.Mpg
                }
        }
    }

    val metricDefs = listOf(
        MetricDef("mpg", mpgLabel, LabChartColors.Mpg, toggles.mpg) { setToggles(toggles.copy(mpg = it)) },
        MetricDef("gpm", gpmLabel, LabChartColors.Gpm, toggles.gpm) { setToggles(toggles.copy(gpm = it)) },
        MetricDef("up", unitPriceLabel, Color(0xFF0277BD), toggles.unitPrice) {
            setToggles(toggles.copy(unitPrice = it))
        },
        MetricDef("dpmf", "$dpmLabel fuel", LabChartColors.DpmFuel, toggles.dpmFuel) {
            setToggles(toggles.copy(dpmFuel = it))
        },
        MetricDef("dpmi", "$dpmLabel +exp", LabChartColors.DpmIncl, toggles.dpmIncl) {
            setToggles(toggles.copy(dpmIncl = it))
        },
        MetricDef("mf", "Fuel $", LabChartColors.DpmFuel, toggles.monthlyFuel) {
            setToggles(toggles.copy(monthlyFuel = it))
        },
        MetricDef("mo", "Other $", Color(0xFF5D4037), toggles.monthlyOther) {
            setToggles(toggles.copy(monthlyOther = it))
        },
        MetricDef("tm", "Trip miles", Color(0xFFEF6C00), toggles.tripMiles) {
            setToggles(toggles.copy(tripMiles = it))
        },
        MetricDef("tp", "Trip %", LabChartColors.DpmIncl, toggles.tripPct) {
            setToggles(toggles.copy(tripPct = it))
        },
    )

    ReportsLabScreenScaffold(
        title = "Time based reports",
        infoText = TIME_CHARTS_INFO,
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Time based reports")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    appendLine("Smooth: ${mode.displayLabel(customDays)}")
                    appendLine(
                        "Metrics: " + metricDefs.filter { it.checked }.joinToString(", ") { it.label }
                            .ifBlank { "(none)" },
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
                subject = "Time based reports",
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
                    val sections = mutableListOf<ReportsLabPdf.PdfSection>()
                    sections += ReportsLabPdf.PdfSection(
                        heading = "Metrics",
                        lines = metricDefs.filter { it.checked }.map { it.label }.ifEmpty { listOf("(none)") },
                    )
                    if (allSeriesForPdf.isNotEmpty()) {
                        val combinedBmp = ReportsLabPdf.renderLineChartBitmap(
                            series = allSeriesForPdf,
                            seriesColors = seriesColorMap,
                            title = "Combined",
                        )
                        sections += ReportsLabPdf.PdfSection(
                            heading = "Combined chart",
                            chartBitmap = combinedBmp,
                        )
                        // Combined table of series names
                        sections += ReportsLabPdf.PdfSection(
                            heading = "Combined series list",
                            lines = allSeriesForPdf.map { (n, pts) -> "$n (${pts.size} points)" },
                        )
                        if (allSeriesForPdf.size > 1 || data.filter.vehicleMode == LabVehicleMode.EACH) {
                            allSeriesForPdf.forEach { (name, pts) ->
                                val one = mapOf(name to pts)
                                val bmp = ReportsLabPdf.renderLineChartBitmap(
                                    series = one,
                                    seriesColors = seriesColorMap,
                                    title = name,
                                )
                                sections += ReportsLabPdf.PdfSection(
                                    heading = name,
                                    chartBitmap = bmp,
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
                                    tableRows = listOf(listOf("date", "value")) +
                                        pts.sortedBy { it.timestampMs }.map {
                                            listOf(formatLabDate(it.timestampMs), "%.4f".format(it.y))
                                        },
                                )
                            }
                        }
                    }
                    ReportsLabPdf.buildTextReportPdf(
                        title = "Time based reports",
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
        AdaptiveItemGrid(items = metricDefs) { def ->
            MetricChipRow(def)
        }

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
        // Color legend tags (match series)
        if (allSeriesForPdf.isNotEmpty()) {
            Text("Series", style = MaterialTheme.typography.titleSmall)
            AdaptiveItemGrid(items = allSeriesForPdf.keys.toList()) { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                seriesColorMap[name] ?: MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(name, style = MaterialTheme.typography.labelSmall, softWrap = true, maxLines = 2)
                }
            }
        }

        when {
            !toggles.anyOn -> ReportsLabEmpty("No metrics selected")
            !hasEconomy && !hasMoneyOrTrip -> ReportsLabEmpty("Not enough points for a chart.")
            else -> {
                LabMultiAxisTimeSeriesChart(
                    startSeries = startSeries,
                    endSeries = endSeries,
                    caption = caption,
                    emptyMessage = "Not enough points for a chart.",
                    startAxisLabel = when {
                        startSeries.isEmpty() -> null
                        mpgSeries.isNotEmpty() && gpmSeries.isNotEmpty() -> "$mpgLabel / $gpmLabel"
                        mpgSeries.isNotEmpty() -> mpgLabel
                        gpmSeries.isNotEmpty() -> gpmLabel
                        else -> "economy"
                    },
                    endAxisLabel = if (endSeries.isNotEmpty()) "\$ / trip" else null,
                    startAxisColor = startColor,
                    endAxisColor = endColor,
                )
            }
        }
    }
}

@Composable
private fun MetricChipRow(def: MetricDef) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 180.dp)
            .padding(end = 4.dp),
    ) {
        Checkbox(checked = def.checked, onCheckedChange = def.onChecked)
        Box(
            Modifier
                .size(12.dp)
                .background(def.color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.size(6.dp))
        Text(def.label, style = MaterialTheme.typography.bodyMedium, softWrap = true, maxLines = 2)
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
    baseLabel: String,
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
                        data.vehicleName(vid) + " " + baseLabel
                    else -> baseLabel
                }
                put(label, pts)
            }
        }
    }
}

private const val TIME_CHARTS_INFO =
    "Time based reports: economy (mpg / vol per distance), money (unit price, cost/distance, monthly \$), " +
        "and trip miles/%. All metrics optional. One chart: economy always left (mpg and gpm share left); " +
        "money and trip always right — sides do not swap when toggles change. " +
        "Smooth bins share one calendar grid across metrics. " +
        "Trip miles/% walk odometer steps under open trip types (Personal included). " +
        "Edge-spanning full-fill legs contribute to both bins. " +
        "PDF includes combined + per-series charts and tables."
