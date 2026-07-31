package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CHART_TAG = "ReportsLabCharts"

/** Family colors for efficiency metrics (lines + axis captions). */
object LabChartColors {
    val Mpg = Color(0xFF1565C0) // blue
    val Gpm = Color(0xFF00897B) // teal
    val DpmFuel = Color(0xFF2E7D32) // green
    val DpmIncl = Color(0xFF6A1B9A) // purple
}

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
    startAxisLabel: String? = null,
    startAxisColor: Color? = null,
) {
    LabMultiAxisTimeSeriesChart(
        startSeries = series,
        endSeries = emptyMap(),
        caption = caption,
        emptyMessage = emptyMessage,
        heightDp = heightDp,
        startAxisLabel = startAxisLabel,
        startAxisColor = startAxisColor,
    )
}

/**
 * Single chart: **start (left)** Y for [startSeries], **end (right)** Y for [endSeries].
 * Prefer putting single-family series on **Start** (End-only can be fragile in Vico 3.2.3).
 * Use [key] remount when axis structure changes so layer count matches model partials.
 */
@Composable
fun LabMultiAxisTimeSeriesChart(
    startSeries: Map<String, List<LabTimeYPoint>>,
    endSeries: Map<String, List<LabTimeYPoint>>,
    caption: String,
    emptyMessage: String,
    heightDp: Int = 240,
    startAxisLabel: String? = null,
    endAxisLabel: String? = null,
    startAxisColor: Color? = null,
    endAxisColor: Color? = null,
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
    // Prefer Start for single-family (money-only, gpm-only, mpg-only) to avoid End-only quirks.
    val (left, right) = if (start.isEmpty() && end.isNotEmpty()) {
        end to emptyMap()
    } else {
        start to end
    }
    val seriesKey = buildString {
        append("L${left.size}R${right.size}|")
        left.forEach { (k, v) -> append("S$k:${v.size}|") }
        right.forEach { (k, v) -> append("E$k:${v.size}|") }
    }
    val leftLabel = if (start.isEmpty() && end.isNotEmpty()) endAxisLabel else startAxisLabel
    val leftColor = if (start.isEmpty() && end.isNotEmpty()) endAxisColor else startAxisColor
    val rightLabel = if (start.isEmpty() && end.isNotEmpty()) null else endAxisLabel
    val rightColor = if (start.isEmpty() && end.isNotEmpty()) null else endAxisColor

    key(seriesKey) {
        val modelProducer = remember(seriesKey) { CartesianChartModelProducer() }
        LaunchedEffect(seriesKey) {
            try {
                modelProducer.runTransaction {
                    if (left.isNotEmpty()) {
                        lineModel {
                            for ((keyName, pts) in left) {
                                val sorted = pts.sortedBy { it.timestampMs }
                                if (sorted.size < 1) continue
                                series(
                                    sorted.map { tsToChartX(it.timestampMs) },
                                    sorted.map { it.y.toDouble() },
                                    keyName,
                                )
                            }
                        }
                    }
                    if (right.isNotEmpty()) {
                        lineModel {
                            for ((keyName, pts) in right) {
                                val sorted = pts.sortedBy { it.timestampMs }
                                if (sorted.size < 1) continue
                                series(
                                    sorted.map { tsToChartX(it.timestampMs) },
                                    sorted.map { it.y.toDouble() },
                                    keyName,
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(CHART_TAG, "Chart model transaction failed", e)
            }
        }
        val dateFmt = rememberDateXFormatter()
        val scroll = rememberVicoScrollState(scrollEnabled = false)
        Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
        val legendParts = mutableListOf<String>()
        if (left.isNotEmpty()) {
            legendParts += "Left: ${left.keys.joinToString(" · ")}"
        }
        if (right.isNotEmpty()) {
            legendParts += "Right: ${right.keys.joinToString(" · ")}"
        }
        if (legendParts.isNotEmpty()) {
            Text(
                legendParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
        }
        if (leftLabel != null) {
            Text(
                leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = leftColor ?: MaterialTheme.colorScheme.primary,
                softWrap = true,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (rightLabel != null) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = rightColor ?: MaterialTheme.colorScheme.tertiary,
                softWrap = true,
            )
        }
        val startLayer = if (left.isNotEmpty()) {
            rememberLineCartesianLayer(verticalAxisPosition = Axis.Position.Vertical.Start)
        } else {
            null
        }
        val endLayer = if (right.isNotEmpty()) {
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
            else -> return@key
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
}

@Composable
fun LabMpgLineChart(
    yValues: List<Float>,
    emptyMessage: String = "Not enough ${UnitFormat.economyEfficiencyLabel()} legs for a chart (need ≥2).",
) {
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
        try {
            modelProducer.runTransaction {
                columnModel {
                    series(fuelAmounts.map { it.toDouble() }, "fuel")
                    series(otherAmounts.map { it.toDouble() }, "other")
                }
            }
        } catch (e: Exception) {
            Log.e(CHART_TAG, "Monthly bars transaction failed", e)
        }
    }
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    val monthFmt = remember(monthKeys) {
        CartesianValueFormatter { _, value, _ ->
            val i = value.toInt()
            monthKeys.getOrNull(i) ?: ""
        }
    }
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = monthFmt),
        ),
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

/**
 * Multi-series index chart (X = 0..n-1) with optional category labels on the bottom axis.
 * Used for monthly Each (one series per vehicle) and category Each (series per vehicle).
 */
@Composable
fun LabMultiSeriesIndexChart(
    series: Map<String, List<Float>>,
    xLabels: List<String>,
    caption: String,
    emptyMessage: String = "Not enough data for a chart.",
    heightDp: Int = 220,
) {
    val clean = series.filter { it.value.isNotEmpty() }
    if (clean.isEmpty() || xLabels.isEmpty()) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    val key = clean.entries.joinToString("|") { (k, v) -> "$k:${v.joinToString()}" } + xLabels.joinToString()
    LaunchedEffect(key) {
        try {
            modelProducer.runTransaction {
                columnModel {
                    for ((name, amounts) in clean) {
                        val padded = xLabels.indices.map { i -> amounts.getOrElse(i) { 0f }.toDouble() }
                        series(padded, name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(CHART_TAG, "Multi-series index chart failed", e)
        }
    }
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    val labelFmt = remember(xLabels) {
        CartesianValueFormatter { _, value, _ ->
            xLabels.getOrNull(value.toInt()) ?: ""
        }
    }
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    Text(
        "Series: ${clean.keys.joinToString(" · ")}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = true,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFmt),
        ),
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
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
        try {
            modelProducer.runTransaction {
                columnModel {
                    series(amounts.map { it.toDouble() }, "cat")
                }
            }
        } catch (e: Exception) {
            Log.e(CHART_TAG, "Category bars failed", e)
        }
    }
    val scroll = rememberVicoScrollState(scrollEnabled = false)
    val catFmt = remember(categoryLabels) {
        CartesianValueFormatter { _, value, _ ->
            categoryLabels.getOrNull(value.toInt()) ?: ""
        }
    }
    Text(caption, style = MaterialTheme.typography.labelMedium, softWrap = true)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = if (categoryLabels.isNotEmpty()) {
                HorizontalAxis.rememberBottom(valueFormatter = catFmt)
            } else {
                HorizontalAxis.rememberBottom()
            },
        ),
        modelProducer = modelProducer,
        scrollState = scroll,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
