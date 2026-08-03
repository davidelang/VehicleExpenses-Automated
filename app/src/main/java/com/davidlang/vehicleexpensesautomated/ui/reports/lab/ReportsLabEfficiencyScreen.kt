package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.R

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

private data class EffMetricToggles(
    val mpg: Boolean = true,
    val gpm: Boolean = false,
    val dpmFuel: Boolean = true,
    val dpmIncl: Boolean = false,
)

private object EffMetricPrefs {
    private const val PREFS = "vehicle_settings"
    fun load(context: Context): EffMetricToggles {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return EffMetricToggles(
            mpg = p.getBoolean("reports_lab_eff_mpg", true),
            gpm = p.getBoolean("reports_lab_eff_gpm", false),
            dpmFuel = p.getBoolean("reports_lab_eff_dpm_fuel", true),
            dpmIncl = p.getBoolean("reports_lab_eff_dpm_incl", false),
        )
    }

    fun save(context: Context, t: EffMetricToggles) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("reports_lab_eff_mpg", t.mpg)
            .putBoolean("reports_lab_eff_gpm", t.gpm)
            .putBoolean("reports_lab_eff_dpm_fuel", t.dpmFuel)
            .putBoolean("reports_lab_eff_dpm_incl", t.dpmIncl)
            .apply()
    }
}

@Composable
fun ReportsLabEfficiencyScreen(navController: NavHostController) {
    val context = LocalContext.current
    val data = rememberLabReportData()
    var toggles by remember { mutableStateOf(EffMetricPrefs.load(context)) }

    fun setToggles(next: EffMetricToggles) {
        toggles = next
        EffMetricPrefs.save(context, next)
    }

    val byScope = remember(data.fuel, data.filter.vehicleMode) { data.fuelByVehicleScope() }
    val metricsByScope = remember(byScope, data.expenses, data.defaultStored) {
        byScope.mapValues { (_, fuel) ->
            val legs = allValidLegsChrono(fuel, data.defaultStored)
            val display = excludeMpgOutliers(legs)
            labLegMetrics(display, fuel, data.expenses, data.defaultStored)
        }
    }

    fun seriesPrefix(vid: Int?): String =
        when {
            data.filter.vehicleMode == LabVehicleMode.EACH && vid != null ->
                data.vehicleName(vid) + " "
            else -> ""
        }

    val mpgSeries = remember(metricsByScope, toggles.mpg, data.filter.vehicleMode) {
        if (!toggles.mpg) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    put(
                        seriesPrefix(vid) + UnitFormat.economyEfficiencyLabel(context),
                        metrics.map {
                            LabTimeYPoint(it.leg.endTimestamp, it.mpg.toFloat(), "mpg")
                        },
                    )
                }
            }.filterValues { it.isNotEmpty() }
        }
    }
    val gpmSeries = remember(metricsByScope, toggles.gpm, data.filter.vehicleMode) {
        if (!toggles.gpm) emptyMap()
        else {
            buildMap {
                for ((vid, metrics) in metricsByScope) {
                    put(
                        seriesPrefix(vid) + "gpm",
                        metrics.mapNotNull { m ->
                            m.gpm?.let { LabTimeYPoint(m.leg.endTimestamp, it.toFloat(), "gpm") }
                        },
                    )
                }
            }.filterValues { it.isNotEmpty() }
        }
    }
    val moneySeries = remember(metricsByScope, toggles.dpmFuel, toggles.dpmIncl, data.filter.vehicleMode) {
        buildMap {
            for ((vid, metrics) in metricsByScope) {
                val prefix = seriesPrefix(vid)
                if (toggles.dpmFuel) {
                    put(
                        prefix + UnitFormat.costPerDistanceLabel(context) + " fuel",
                        metrics.mapNotNull { m ->
                            m.dpmFuel?.let {
                                LabTimeYPoint(m.leg.endTimestamp, it.toFloat(), "dpmFuel")
                            }
                        },
                    )
                }
                if (toggles.dpmIncl) {
                    put(
                        prefix + UnitFormat.costPerDistanceLabel(context) + " +exp",
                        metrics.mapNotNull { m ->
                            m.dpmInclExp?.let {
                                LabTimeYPoint(m.leg.endTimestamp, it.toFloat(), "dpmIncl")
                            }
                        },
                    )
                }
            }
        }.filterValues { it.isNotEmpty() }
    }

    val anyMetricOn = toggles.mpg || toggles.gpm || toggles.dpmFuel || toggles.dpmIncl
    val hasMpg = mpgSeries.isNotEmpty()
    val hasGpm = gpmSeries.isNotEmpty()
    val hasMoney = moneySeries.isNotEmpty()

    val allMetricsFlat = metricsByScope.values.flatten()
    val allLegs = metricsByScope.values.flatMap { list -> list.map { it.leg } }

    val moneyAxisColor = when {
        toggles.dpmFuel && !toggles.dpmIncl -> LabChartColors.DpmFuel
        !toggles.dpmFuel && toggles.dpmIncl -> LabChartColors.DpmIncl
        else -> LabChartColors.DpmFuel
    }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_fuel_efficiency),
        infoText = "Economy & cost/distance over full-fill legs (same chain rules as production). " +
            "Toggles: mpg, gpm, ${UnitFormat.costPerDistanceLabel(context)} fuel-only, " +
            "${UnitFormat.costPerDistanceLabel(context)} incl. expenses — all optional (including none). " +
            "Gpm uses its own Y scale (not shared with mpg). " +
            "When gpm and \$/mi are both on, money is a second chart below. " +
            "Charts use a date X axis and fit screen width. " +
            stringResource(R.string.reports_each_vehicle_multi_series_per_family),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Fuel efficiency")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    appendLine(
                        "Metrics: " +
                            listOfNotNull(
                                if (toggles.mpg) "mpg" else null,
                                if (toggles.gpm) "gpm" else null,
                                if (toggles.dpmFuel) "\$/mi fuel" else null,
                                if (toggles.dpmIncl) "\$/mi+exp" else null,
                            ).joinToString(", ").ifBlank { "(none)" },
                    )
                    if (toggles.mpg) {
                        appendLine(
                            "Last mpg: ${formatMpg(lastMpg(allLegs))} · Avg mpg: ${formatMpg(avgMpg(allLegs))}",
                        )
                    }
                    appendLine("Legs: ${allMetricsFlat.size}")
                    allMetricsFlat.sortedBy { it.leg.endTimestamp }.forEach { m ->
                        val parts = mutableListOf(
                            formatLabDate(m.leg.endTimestamp),
                            data.vehicleName(m.leg.vehicleId),
                            "odo ${m.leg.endFill.odometer}",
                            "mi ${m.leg.miles}",
                        )
                        if (toggles.mpg) parts += "mpg ${formatMpg(m.mpg)}"
                        if (toggles.gpm) parts += "gpm ${m.gpm?.let { "%.4f".format(it) } ?: "n/a"}"
                        if (toggles.dpmFuel) {
                            parts += "dpmF ${m.dpmFuel?.let { "%.4f".format(it) } ?: "n/a"}"
                        }
                        if (toggles.dpmIncl) {
                            parts += "dpmX ${m.dpmInclExp?.let { "%.4f".format(it) } ?: "n/a"}"
                        }
                        appendLine(parts.joinToString(" "))
                    }
                }
            }
            ReportsLabShareActions(
                subject = "Fuel efficiency",
                textBody = buildText,
                csvFileName = "lab_efficiency.csv",
                csvBody = {
                    val headers = mutableListOf("date", "vehicle", "odo", "miles", "volume", "cost_summary")
                    if (toggles.mpg) headers += "mpg"
                    if (toggles.gpm) headers += "gpm"
                    if (toggles.dpmFuel) headers += "dpm_fuel"
                    if (toggles.dpmIncl) headers += "dpm_incl_exp"
                    val sb = StringBuilder(headers.joinToString(",") + "\n")
                    allMetricsFlat.sortedBy { it.leg.endTimestamp }.forEach { m ->
                        val row = mutableListOf(
                            formatLabDate(m.leg.endTimestamp),
                            ReportsLabShare.csvEscape(data.vehicleName(m.leg.vehicleId)),
                            m.leg.endFill.odometer.toString(),
                            m.leg.miles.toString(),
                            "%.4f".format(m.leg.sumVol),
                            ReportsLabShare.csvEscape(
                                CurrencyCodes.formatAggregateSum(
                                    m.leg.sumCostByCurrency,
                                    data.defaultSymbol,
                                ),
                            ),
                        )
                        if (toggles.mpg) row += "%.3f".format(m.mpg)
                        if (toggles.gpm) row += m.gpm?.let { "%.5f".format(it) } ?: ""
                        if (toggles.dpmFuel) row += m.dpmFuel?.let { "%.5f".format(it) } ?: ""
                        if (toggles.dpmIncl) row += m.dpmInclExp?.let { "%.5f".format(it) } ?: ""
                        sb.append(row.joinToString(",")).append('\n')
                    }
                    sb.toString()
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Fuel efficiency", buildText()) },
            )
        },
    ) {
        if (data.fuel.isEmpty()) {
            ReportsLabEmpty("No fills in this filter.")
            return@ReportsLabScreenScaffold
        }
        Text(stringResource(R.string.reports_metrics), style = MaterialTheme.typography.titleSmall)
        MetricToggleRow(
            label = UnitFormat.economyEfficiencyLabel(context),
            checked = toggles.mpg,
            onChecked = { setToggles(toggles.copy(mpg = it)) },
        )
        MetricToggleRow(
            label = "gpm (vol / mi)",
            checked = toggles.gpm,
            onChecked = { setToggles(toggles.copy(gpm = it)) },
        )
        MetricToggleRow(
            label = "${UnitFormat.costPerDistanceLabel(context)} (fuel only)",
            checked = toggles.dpmFuel,
            onChecked = { setToggles(toggles.copy(dpmFuel = it)) },
        )
        MetricToggleRow(
            label = "${UnitFormat.costPerDistanceLabel(context)} incl. expenses",
            checked = toggles.dpmIncl,
            onChecked = { setToggles(toggles.copy(dpmIncl = it)) },
        )
        if (toggles.mpg) {
            Text(
                "Last mpg: ${formatMpg(lastMpg(allLegs))} · Avg mpg: ${formatMpg(avgMpg(allLegs))} · " +
                    "Legs: ${allMetricsFlat.size}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(stringResource(R.string.reports_display_avg_last_exclude_mpg_outside_5_80_and_3_),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            !anyMetricOn -> {
                ReportsLabEmpty("No metrics selected")
            }
            !hasMpg && !hasGpm && !hasMoney -> {
                ReportsLabEmpty("Not enough points for a chart.")
            }
            // A4: gpm + money → economy host + separate money host
            hasGpm && hasMoney -> {
                LabMultiAxisTimeSeriesChart(
                    startSeries = mpgSeries,
                    endSeries = gpmSeries,
                    caption = "Economy — mpg (left) + gpm (right); separate scales",
                    emptyMessage = "Not enough economy points for a chart.",
                    startAxisLabel = if (hasMpg) UnitFormat.economyEfficiencyLabel(context) else null,
                    endAxisLabel = "gpm",
                    startAxisColor = LabChartColors.Mpg,
                    endAxisColor = LabChartColors.Gpm,
                )
                Spacer(Modifier.height(8.dp))
                LabMultiAxisTimeSeriesChart(
                    startSeries = moneySeries,
                    endSeries = emptyMap(),
                    caption = "Cost per distance (separate chart / scale)",
                    emptyMessage = "Not enough cost/distance points for a chart.",
                    startAxisLabel = UnitFormat.costPerDistanceLabel(context),
                    startAxisColor = moneyAxisColor,
                )
            }
            // Money only, or mpg + money without gpm (End→Start flip when mpg empty)
            hasMoney && !hasGpm -> {
                LabMultiAxisTimeSeriesChart(
                    startSeries = mpgSeries,
                    endSeries = moneySeries,
                    caption = when {
                        hasMpg -> "mpg (left) + cost/distance (right)"
                        else -> "Cost per distance"
                    },
                    emptyMessage = "Not enough points for a chart.",
                    startAxisLabel = if (hasMpg) UnitFormat.economyEfficiencyLabel(context) else null,
                    endAxisLabel = UnitFormat.costPerDistanceLabel(context),
                    startAxisColor = if (hasMpg) LabChartColors.Mpg else null,
                    endAxisColor = moneyAxisColor,
                )
            }
            // Economy only (mpg and/or gpm; no money)
            else -> {
                LabMultiAxisTimeSeriesChart(
                    startSeries = mpgSeries,
                    endSeries = gpmSeries,
                    caption = when {
                        hasMpg && hasGpm -> "mpg (left) + gpm (right); separate scales"
                        hasMpg -> UnitFormat.economyEfficiencyLabel(context)
                        else -> "gpm"
                    },
                    emptyMessage = "Not enough points for a chart.",
                    startAxisLabel = if (hasMpg) UnitFormat.economyEfficiencyLabel(context) else null,
                    endAxisLabel = if (hasGpm) "gpm" else null,
                    startAxisColor = if (hasMpg) LabChartColors.Mpg else null,
                    endAxisColor = if (hasGpm) LabChartColors.Gpm else null,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reports_legs_newest_first), style = MaterialTheme.typography.titleSmall)
        if (allMetricsFlat.isEmpty()) {
            ReportsLabEmpty("No valid full-fill legs in this filter.")
        } else {
            allMetricsFlat.sortedByDescending { it.leg.endTimestamp }.forEach { m ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        "${formatLabDate(m.leg.endTimestamp)} · ${data.vehicleName(m.leg.vehicleId)} · " +
                            "odo ${m.leg.endFill.odometer}",
                    )
                    Text(
                        buildString {
                            if (toggles.mpg) append("mpg ${formatMpg(m.mpg)} · ")
                            if (toggles.gpm) {
                                append("gpm ${m.gpm?.let { "%.4f".format(it) } ?: "n/a"} · ")
                            }
                            if (toggles.dpmFuel) {
                                append("dpmF ${m.dpmFuel?.let { "%.3f".format(it) } ?: "n/a"} · ")
                            }
                            if (toggles.dpmIncl) {
                                append("dpmX ${m.dpmInclExp?.let { "%.3f".format(it) } ?: "n/a"} · ")
                            }
                            append(formatVolume(m.leg.sumVol, data.volumeLabel))
                            append(" · ")
                            append(
                                CurrencyCodes.formatAggregateSum(
                                    m.leg.sumCostByCurrency,
                                    data.defaultSymbol,
                                ),
                            )
                            append(" · ${UnitFormat.distanceDeltaLabel(m.leg.miles, context)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, style = MaterialTheme.typography.bodyMedium, softWrap = true)
    }
}
