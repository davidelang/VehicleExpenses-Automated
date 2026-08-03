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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CHART_TAG = "ReportsLabCharts"

/** Family colors for efficiency metrics (lines + axis ticks + captions). */
object LabChartColors {
    val Mpg = Color(0xFF1565C0) // blue
    val Gpm = Color(0xFF00897B) // teal
    val DpmFuel = Color(0xFF2E7D32) // green
    val DpmIncl = Color(0xFF6A1B9A) // purple
}

/**
 * Map a series legend key to its metric family color when recognizable
 * (efficiency screen keys: mpg / gpm / cost-per-distance fuel / +exp).
 */
fun familyColorForSeriesKey(key: String): Color? {
    val k = key.lowercase()
    return when {
        k.contains("+exp") || k.contains("incl") -> LabChartColors.DpmIncl
        k.contains("trip %") -> LabChartColors.DpmIncl
        k.contains("trip miles") || k.contains("trip mi") -> Color(0xFFEF6C00)
        // $/G, $/L unit price (not $/mi)
        (k.contains("$/") || k.contains("\$/")) &&
            (k.endsWith("/g") || k.endsWith("/l") || k.contains("/g") || k.contains("/l")) &&
            !k.contains("/mi") && !k.contains("/km") -> Color(0xFF0277BD)
        k.endsWith(" fuel") || k == "fuel $" || k.endsWith(" fuel $") ||
            (k.contains("fuel") && (k.contains("/mi") || k.contains("/km") || k.contains("per"))) ->
            LabChartColors.DpmFuel
        k.contains("other $") -> Color(0xFF5D4037)
        // G/mi, L/mi volume-per-distance
        k.contains("gpm") || k.contains("/mi") && (k.contains("g/") || k.contains("l/") ||
            k.startsWith("g/") || k.startsWith("l/") ||
            Regex("""\b[gl]/mi\b""").containsMatchIn(k)) -> LabChartColors.Gpm
        k.contains("mpg") || k.contains("l/100") || k.contains("km/l") || k.contains("kpl") ->
            LabChartColors.Mpg
        else -> null
    }
}

/**
 * Stroke color for one series: family hue, with shade variation when several share an axis
 * (Each vehicle, or dual $/mi on one money host).
 */
fun seriesStrokeColor(
    key: String,
    familyDefault: Color,
    index: Int,
    total: Int,
): Color {
    val base = familyColorForSeriesKey(key) ?: familyDefault
    if (total <= 1) return base
    // Darken later series; keep hue in family (L2).
    val t = index.toFloat() / (total - 1).coerceAtLeast(1).toFloat()
    val factor = 1f - 0.35f * t
    return Color(
        red = (base.red * factor).coerceIn(0f, 1f),
        green = (base.green * factor).coerceIn(0f, 1f),
        blue = (base.blue * factor).coerceIn(0f, 1f),
        alpha = base.alpha,
    )
}

@Composable
private fun rememberFamilyLineLayer(
    seriesKeys: Collection<String>,
    familyDefault: Color?,
    verticalAxisPosition: Axis.Position.Vertical,
): LineCartesianLayer {
    val keys = seriesKeys.toList()
    // Only apply fixed family colors when caller provides a family default (efficiency).
    // Cost trends / other callers keep Vico default rainbow.
    if (familyDefault == null || keys.isEmpty()) {
        return rememberLineCartesianLayer(verticalAxisPosition = verticalAxisPosition)
    }
    val lines = remember(keys, familyDefault) {
        keys.mapIndexed { index, keyName ->
            val c = seriesStrokeColor(keyName, familyDefault, index, keys.size)
            val stroke: LineCartesianLayer.LineStroke =
                if (keys.size > 1 && index % 2 == 1) {
                    LineCartesianLayer.LineStroke.Dashed(
                        thickness = 2.dp,
                        dashLength = 8.dp,
                        gapLength = 4.dp,
                    )
                } else {
                    LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp)
                }
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(c)),
                stroke = stroke,
            )
        }
    }
    return rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(lines),
        verticalAxisPosition = verticalAxisPosition,
    )
}

@Composable
private fun rememberFamilyStartAxis(color: Color?) =
    if (color == null) {
        VerticalAxis.rememberStart()
    } else {
        val fill = Fill(color)
        VerticalAxis.rememberStart(
            line = rememberAxisLineComponent(fill = fill),
            label = rememberAxisLabelComponent(
                style = TextStyle(color = color, fontSize = 10.sp),
            ),
            tick = rememberAxisTickComponent(fill = fill),
        )
    }

@Composable
private fun rememberFamilyEndAxis(color: Color?) =
    if (color == null) {
        VerticalAxis.rememberEnd()
    } else {
        val fill = Fill(color)
        VerticalAxis.rememberEnd(
            line = rememberAxisLineComponent(fill = fill),
            label = rememberAxisLabelComponent(
                style = TextStyle(color = color, fontSize = 10.sp),
            ),
            tick = rememberAxisTickComponent(fill = fill),
        )
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
    val start = startSeries.filter { it.value.isNotEmpty() }
    val end = endSeries.filter { it.value.isNotEmpty() }
    val totalPts = start.values.sumOf { it.size } + end.values.sumOf { it.size }
    if ((start.isEmpty() && end.isEmpty()) || totalPts < 2) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    // Prefer Start for single-family (money-only, gpm-only, mpg-only) to avoid End-only quirks.
    val flippedMoneyOnly = start.isEmpty() && end.isNotEmpty()
    val left = if (flippedMoneyOnly) end else start
    val right: Map<String, List<LabTimeYPoint>> =
        if (flippedMoneyOnly) emptyMap() else end
    val seriesKey = buildString {
        append("L${left.size}R${right.size}|")
        left.forEach { (k, v) -> append("S$k:${v.size}|") }
        right.forEach { (k, v) -> append("E$k:${v.size}|") }
    }
    val leftLabel = if (flippedMoneyOnly) endAxisLabel else startAxisLabel
    val leftColor = if (flippedMoneyOnly) endAxisColor else startAxisColor
    val rightLabel = if (flippedMoneyOnly) null else endAxisLabel
    val rightColor = if (flippedMoneyOnly) null else endAxisColor

    // Remount host when axis structure changes (avoids layer/partial mismatch).
    // No non-local return@key — D8 rejects those synthetic methods.
    key(seriesKey) {
        LabMultiAxisTimeSeriesChartBody(
            left = left,
            right = right,
            seriesKey = seriesKey,
            caption = caption,
            heightDp = heightDp,
            leftLabel = leftLabel,
            rightLabel = rightLabel,
            leftColor = leftColor,
            rightColor = rightColor,
        )
    }
}

@Composable
private fun LabMultiAxisTimeSeriesChartBody(
    left: Map<String, List<LabTimeYPoint>>,
    right: Map<String, List<LabTimeYPoint>>,
    seriesKey: String,
    caption: String,
    heightDp: Int,
    leftLabel: String?,
    rightLabel: String?,
    leftColor: Color?,
    rightColor: Color?,
) {
    val modelProducer = remember(seriesKey) { CartesianChartModelProducer() }
    LaunchedEffect(seriesKey) {
        try {
            modelProducer.runTransaction {
                if (left.isNotEmpty()) {
                    lineModel {
                        for ((keyName, pts) in left) {
                            val sorted = pts.sortedBy { it.timestampMs }
                            if (sorted.isEmpty()) continue
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
                            if (sorted.isEmpty()) continue
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
    val hasLeft = left.isNotEmpty()
    val hasRight = right.isNotEmpty()
    // Order of keys must match lineModel series order (map insertion order).
    val startLayer = if (hasLeft) {
        rememberFamilyLineLayer(
            seriesKeys = left.keys,
            familyDefault = leftColor,
            verticalAxisPosition = Axis.Position.Vertical.Start,
        )
    } else {
        null
    }
    val endLayer = if (hasRight) {
        rememberFamilyLineLayer(
            seriesKeys = right.keys,
            familyDefault = rightColor,
            verticalAxisPosition = Axis.Position.Vertical.End,
        )
    } else {
        null
    }
    val startAxis = if (hasLeft) rememberFamilyStartAxis(leftColor) else null
    val endAxis = if (hasRight) rememberFamilyEndAxis(rightColor) else null
    when {
        startLayer != null && endLayer != null && startAxis != null && endAxis != null -> {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    startLayer,
                    endLayer,
                    startAxis = startAxis,
                    endAxis = endAxis,
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
                ),
                modelProducer = modelProducer,
                scrollState = scroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp),
            )
        }
        startLayer != null && startAxis != null -> {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    startLayer,
                    startAxis = startAxis,
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
                ),
                modelProducer = modelProducer,
                scrollState = scroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp),
            )
        }
        endLayer != null && endAxis != null -> {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    endLayer,
                    endAxis = endAxis,
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFmt),
                ),
                modelProducer = modelProducer,
                scrollState = scroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp),
            )
        }
    }
}

@Composable
fun LabMpgLineChart(
    yValues: List<Float>,
    emptyMessage: String? = null,
) {
    val context = LocalContext.current
    val eff = UnitFormat.economyEfficiencyLabel(context)
    val empty = emptyMessage
        ?: "Not enough $eff legs for a chart (need ≥2)."
    if (yValues.size < 2) {
        ReportsLabEmpty(empty)
        return
    }
    val now = System.currentTimeMillis()
    val day = TimeUnit.DAYS.toMillis(1)
    val pts = yValues.mapIndexed { i, y ->
        LabTimeYPoint(timestampMs = now - (yValues.size - 1 - i) * day, y = y)
    }
    LabTimeSeriesLineChart(
        series = mapOf(eff to pts),
        caption = "$eff over full-fill legs (chronological)",
        emptyMessage = empty,
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
