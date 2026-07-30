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
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

/**
 * Vico 3.2.3 chart helpers for Reports Lab.
 * API: compose.cartesian.data lineModel/columnModel + CartesianChartModelProducer.
 */
@Composable
fun LabMpgLineChart(
    yValues: List<Float>,
    emptyMessage: String = "Not enough ${UnitFormat.economyEfficiencyLabel()} legs for a chart (need ≥2).",
) {
    if (yValues.size < 2) {
        ReportsLabEmpty(emptyMessage)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(yValues) {
        modelProducer.runTransaction {
            lineModel {
                series(yValues.map { it.toDouble() })
            }
        }
    }
    Text(
        "${UnitFormat.economyEfficiencyLabel()} over full-fill legs (chronological)",
        style = MaterialTheme.typography.labelMedium,
        softWrap = true,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    )
}

@Composable
fun LabUnitPriceLineChart(yValues: List<Float>) {
    if (yValues.size < 2) {
        ReportsLabEmpty("Not enough unit-price points for a chart (need ≥2 fills with cost and volume).")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(yValues) {
        modelProducer.runTransaction {
            lineModel {
                series(yValues.map { it.toDouble() })
            }
        }
    }
    Text("Unit price (cost ÷ volume) over fills", style = MaterialTheme.typography.labelMedium)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    )
}

@Composable
fun LabMonthlyBarsChart(
    fuelAmounts: List<Float>,
    otherAmounts: List<Float>,
    caption: String,
) {
    if (fuelAmounts.isEmpty()) {
        ReportsLabEmpty("No monthly cost data for a chart.")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(fuelAmounts, otherAmounts) {
        modelProducer.runTransaction {
            columnModel {
                series(fuelAmounts.map { it.toDouble() })
                series(otherAmounts.map { it.toDouble() })
            }
        }
    }
    Text(caption, style = MaterialTheme.typography.labelMedium)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

@Composable
fun LabCategoryBarsChart(
    amounts: List<Float>,
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
                series(amounts.map { it.toDouble() })
            }
        }
    }
    Text(caption, style = MaterialTheme.typography.labelMedium)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
