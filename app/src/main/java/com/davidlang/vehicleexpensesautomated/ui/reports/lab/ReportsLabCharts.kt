package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Convert epoch ms → X unit (fractional days) for time-series charts.
 * Quantized to **4 decimal places** so Vico 3.2.3 GCD step computation does not
 * throw `IllegalArgumentException: The x-values are too precise`.
 */
fun tsToChartX(timestampMs: Long): Double {
    val days = timestampMs.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble()
    return kotlin.math.round(days * 10_000.0) / 10_000.0
}

private fun chartXToDateLabel(x: Double): String {
    val ms = (x * TimeUnit.DAYS.toMillis(1).toDouble()).toLong()
    return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ms))
}

@Composable
private fun rememberDateXFormatter(): CartesianValueFormatter {
    return remember {
        CartesianValueFormatter { _, value, _ -> chartXToDateLabel(value) }
    }
}

/**
 * Multi-series line chart with **date X** (not index) and **fit-width** (scroll disabled).
 * [series] map key = legend label; values = chronological points.
 */
@Composable
fun LabTimeSeriesLineChart(
    series: Map<String, List<LabTimeYPoint>>,
    caption: String,
    emptyMessage: String,
    heightDp: Int = 200,
) {
    LabMultiAxisTimeSeriesChart(
        startSeries = series,
        endSeries = emptyMap(),
        caption = caption,
        emptyMessage = emptyMessage,
        heightDp = heightDp,
    )
}

/**
 * Single chart: **start (left)** Y for [startSeries] (mpg/gpm), **end (right)** Y for [endSeries] ($/mi).
 * Two [lineModel] partials → two [rememberLineCartesianLayer] with Start/End axis positions.
 * mpg and gpm share the left scale (legend distinguishes); dual independent left axes not used (Vico 3.2.3).
 */
@Composable
fun LabMultiAxisTimeSeriesChart(
    startSeries: Map<String, List<LabTimeYPoint>>,
    endSeries: Map<String, List<LabTimeYPoint>>,
    caption: String,
    emptyMessage: String,
    heightDp: Int = 240,
) {
    fun nonempty(s: Map<String, List<LabTimeYPoint>>) =
        s.filter { it.value.isNotEmpty() }

    val start = nonempty(startSeries)
    val end = nonempty(endSeries)
    val totalPts = start.values.sumOf { it.size } + end.values.sumOf { it.size }
    if ((start.isEmpty() && end.isEmpty()) || totalPts < 2) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    val seriesKey = buildString {
        start.forEach { (k, v) -> append("S$k:${v.joinToString { "${it.timestampMs}:${it.y}" }}|") }
        end.forEach { (k, v) -> append("E$k:${v.joinToString { "${it.timestampMs}:${it.y}" }}|") }
    }
    LaunchedEffect(seriesKey) {
        modelProducer.runTransaction {
            if (start.isNotEmpty()) {
                lineModel {
                    for ((key, pts) in start) {
                        val sorted = pts.sortedBy { it.timestampMs }
                        if (sorted.isEmpty()) continue
                        series(
                            sorted.map { tsToChartX(it.timestampMs) },
                            sorted.map { it.y.toDouble() },
                            key,
                        )
                    }
                }
            }
            if (end.isNotEmpty()) {
                lineModel {
                    for ((key, pts) in end) {
                        val sorted = pts.sortedBy { it.timestampMs }
                        if (sorted.isEmpty()) continue
                        series(
                            sorted.map { tsToChartX(it.timestampMs) },
                            sorted.map { it.y.toDouble() },
                            key,
                        )
                    }
                }
            }
        }
    }
    val dateFmt = rememberDateXFormatter()
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    val legendParts = mutableListOf<String>()
    if (start.isNotEmpty()) {
        legendParts += "Left: ${start.keys.joinToString(" · ")}"
    }
    if (end.isNotEmpty()) {
        legendParts += "Right \$: ${end.keys.joinToString(" · ")}"
    }
    if (legendParts.isNotEmpty()) {
        Text(
            legendParts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
    }
    // Layer order matches lineModel order: first partial → Start, second → End when both present.
    val startLayer = if (start.isNotEmpty()) {
        rememberLineCartesianLayer(verticalAxisPosition = Axis.Position.Vertical.Start)
    } else {
        null
    }
    val endLayer = if (end.isNotEmpty()) {
        rememberLineCartesianLayer(verticalAxisPosition = Axis.Position.Vertical.End)
    } else {
        null
    }
    val chart = when {
        startLayer != null && endLayer != null ->
            rememberCartesianChart(
                startLayer,
                endLayer,
                startAxis = VerticalAxis.rememberStart(),
                endAxis = VerticalAxis.rememberEnd(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
            )
        startLayer != null ->
            rememberCartesianChart(
                startLayer,
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
            )
        endLayer != null ->
            rememberCartesianChart(
                endLayer,
                endAxis = VerticalAxis.rememberEnd(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
            )
        else -> return
    }
    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    )
}

@Composable
fun LabMpgLineChart(
    yValues: List<Float>,
    emptyMessage: String = "Not enough ${UnitFormat.economyEfficiencyLabel()} legs for a chart (need ≥2).",
) {
    // Legacy index-only path — prefer [LabTimeSeriesLineChart] with timestamps.
    if (yValues.size < 2) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    val now = System.currentTimeMillis()
    val day = TimeUnit.DAYS.toMillis(1)
    val pts = yValues.mapIndexed { i, y ->
        LabTimeYPoint(timestampMs = now - (yValues.size - 1 - i) * day, y = y)
    }
    LabTimeSeriesLineChart(
        series = mapOf(UnitFormat.economyEfficiencyLabel() to pts),
        caption = "${UnitFormat.economyEfficiencyLabel()} over full-fill legs (chronological)",
        emptyMessage = emptyMessage,
    )
}

/** Prefer [LabTimeSeriesLineChart] with real timestamps. This keeps index-based y-only callers. */
@Composable
fun LabUnitPriceLineChart(yValues: List<Float>) {
    if (yValues.size < 2) {
        ReportsLabEmpty("Not enough unit-price points for a chart (need ≥2 fills with cost and volume).")
        return
    }
    val base = System.currentTimeMillis() - yValues.size * TimeUnit.DAYS.toMillis(1)
    val pts = yValues.mapIndexed { i, y ->
        LabTimeYPoint(timestampMs = base + i * TimeUnit.DAYS.toMillis(1), y = y)
    }
    LabTimeSeriesLineChart(
        series = mapOf("unit price" to pts),
        caption = "Unit price (cost ÷ volume) over fills (date axis)",
        emptyMessage = "Not enough unit-price points for a chart (need ≥2 fills with cost and volume).",
    )
}

@Composable
fun LabMonthlyBarsChart(
    fuelAmounts: List<Float>,
    otherAmounts: List<Float>,
    monthKeys: List<String> = emptyList(),
    caption: String,
) {
    if (fuelAmounts.isEmpty()) {
        ReportsLabEmpty("No monthly cost data for a chart.")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(fuelAmounts, otherAmounts, monthKeys) {
        modelProducer.runTransaction {
            columnModel {
                // X = month index 0..n-1; labels via caption / keys in UI
                series(fuelAmounts.map { it.toDouble() }, "fuel")
                series(otherAmounts.map { it.toDouble() }, "other")
            }
        }
    }
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    if (monthKeys.isNotEmpty()) {
        Text(
            monthKeys.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
            maxLines = 3,
        )
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

@Composable
fun LabCategoryBarsChart(
    amounts: List<Float>,
    categoryLabels: List<String> = emptyList(),
    caption: String,
) {
    if (amounts.isEmpty()) {
        ReportsLabEmpty("No category totals for a chart.")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(amounts) {
        modelProducer.runTransaction {
            columnModel {
                series(amounts.map { it.toDouble() }, "cat")
            }
        }
    }
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    if (categoryLabels.isNotEmpty()) {
        Text(
            categoryLabels.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
            maxLines = 4,
        )
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
