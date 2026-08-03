package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes

@Composable
fun ReportsLabCostTrendsScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    val fillFuel = remember(data.fuel) { data.fuel.withoutTripStarts() }
    val rows = remember(fillFuel) {
        fillFuel.sortedBy { it.timestamp }.mapNotNull { e ->
            val up = unitPrice(e) ?: return@mapNotNull null
            e to up
        }
    }
    val chartSeries = remember(rows, data.filter.vehicleMode) {
        when (data.filter.vehicleMode) {
            LabVehicleMode.EACH ->
                rows.groupBy { it.first.vehicleId }.mapKeys { (vid, _) ->
                    data.vehicleName(vid)
                }.mapValues { (_, list) ->
                    list.map { (e, up) -> LabTimeYPoint(e.timestamp, up.toFloat()) }
                }
            else ->
                mapOf(
                    "unit price" to rows.map { (e, up) ->
                        LabTimeYPoint(e.timestamp, up.toFloat())
                    },
                )
        }
    }
    val totals = remember(fillFuel, data.defaultStored) {
        CurrencyCodes.sumByCurrency(fillFuel, data.defaultStored, { it.currency }, { it.cost })
    }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_fuel_cost_trends),
        infoText = "Unit price = cost ÷ volume when both are present. " +
            stringResource(R.string.reports_this_is_not_cost_per_distance_that_is_on_vehicle),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Fuel & cost trends")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    appendLine("Fuel total: ${CurrencyCodes.formatAggregateSum(totals, data.defaultSymbol)}")
                    appendLine("Unit-price rows: ${rows.size}")
                    rows.forEach { (e, up) ->
                        appendLine(
                            "${formatLabDate(e.timestamp)} ${data.vehicleName(e.vehicleId)} " +
                                "unit ${"%.3f".format(up)} cost ${CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)} " +
                                "vol ${formatVolume(e.gallons, data.volumeLabel)}",
                        )
                    }
                }
            }
            ReportsLabShareActions(
                subject = "Fuel & cost trends",
                textBody = buildText,
                csvFileName = "lab_cost_trends.csv",
                csvBody = {
                    val sb = StringBuilder("date,vehicle,unit_price,cost,volume,currency\n")
                    rows.forEach { (e, up) ->
                        sb.append(
                            listOf(
                                formatLabDate(e.timestamp),
                                ReportsLabShare.csvEscape(data.vehicleName(e.vehicleId)),
                                "%.4f".format(up),
                                "%.4f".format(e.cost),
                                "%.4f".format(e.gallons),
                                e.currency.ifBlank { data.defaultStored },
                            ).joinToString(","),
                        ).append('\n')
                    }
                    sb.toString()
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Fuel & cost trends", buildText()) },
            )
        },
    ) {
        if (data.fuel.isEmpty()) {
            ReportsLabEmpty("No fills in this filter.")
            return@ReportsLabScreenScaffold
        }
        Text(
            "Fuel total: ${CurrencyCodes.formatAggregateSum(totals, data.defaultSymbol)} · fills with unit price: ${rows.size}",
            style = MaterialTheme.typography.titleSmall,
        )
        LabTimeSeriesLineChart(
            series = chartSeries,
            caption = "Unit price (cost ÷ volume) over fills (date axis)",
            emptyMessage = "Not enough unit-price points for a chart (need ≥2 fills with cost and volume).",
        )
        Spacer(Modifier.height(8.dp))
        rows.asReversed().forEach { (e, up) ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${formatLabDate(e.timestamp)} · ${data.vehicleName(e.vehicleId)}")
                Text(
                    "unit ${"%.3f".format(up)} / ${data.volumeLabel} · " +
                        "${CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)} · " +
                        formatVolume(e.gallons, data.volumeLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
