package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

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
        k.contains(AppStrings.get(R.string.reports_trip)) -> LabChartColors.DpmIncl
        k.contains(AppStrings.get(R.string.reports_trip_miles_2)) || k.contains(AppStrings.get(R.string.reports_trip_mi)) -> Color(0xFFEF6C00)
        // $/G, $/L unit price (not $/mi)
        (k.contains("$/") || k.contains("\$/")) &&
            (k.endsWith("/g") || k.endsWith("/l") || k.contains("/g") || k.contains("/l")) &&
            !k.contains("/mi") && !k.contains("/km") -> Color(0xFF0277BD)
        k.endsWith(AppStrings.get(R.string.reports_fuel)) || k == AppStrings.get(R.string.reports_fuel_2) || k.endsWith(AppStrings.get(R.string.reports_fuel_3)) ||
            (k.contains("fuel") && (k.contains("/mi") || k.contains("/km") || k.contains("per"))) ->
            LabChartColors.DpmFuel
        k.contains(AppStrings.get(R.string.reports_other)) -> Color(0xFF5D4037)
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

/**
 * Multi-series index chart (X = 0..n-1) with optional category labels on the bottom axis.
 * Used for monthly Each (one series per vehicle) and category Each (series per vehicle).
 */
@Composable
fun LabMultiSeriesIndexChart(
    series: Map<String, List<Float>>,
    xLabels: List<String>,
    caption: String,
    emptyMessage: String = AppStrings.get(R.string.reports_not_enough_data_for_a_chart),
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
        // kept: failure stays on this call so the screen or caller can continue
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
        stringResource(R.string.reports_series_1_s, clean.keys.joinToString(" · ")),
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
        ReportsLabEmpty(stringResource(R.string.reports_no_category_totals_for_a_chart))
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
        // kept: failure stays on this call so the screen or caller can continue
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
