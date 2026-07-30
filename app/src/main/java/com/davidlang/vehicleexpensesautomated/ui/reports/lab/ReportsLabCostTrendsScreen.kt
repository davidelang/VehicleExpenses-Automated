package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val chartY = remember(rows) { rows.map { (_, up) -> up.toFloat() } }
    val totals = remember(fillFuel, data.defaultStored) {
        CurrencyCodes.sumByCurrency(fillFuel, data.defaultStored, { it.currency }, { it.cost })
    }

    ReportsLabScreenScaffold(
        title = "Fuel & cost trends",
        subtitle = "Unit price = cost ÷ volume when both are present.",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareRow = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val body = buildString {
                        appendLine("Vehicle Expenses — Fuel & cost trends (experimental)")
                        appendLine("Period: ${periodLabel(data.filter)}")
                        appendLine("Vehicle: ${data.filterVehicleLabel()}")
                        appendLine("Fuel total: ${CurrencyCodes.formatAggregateSum(totals, data.defaultSymbol)}")
                        appendLine("Unit-price rows: ${rows.size}")
                        rows.takeLast(80).forEach { (e, up) ->
                            appendLine(
                                "${formatLabDate(e.timestamp)} ${data.vehicleName(e.vehicleId)} " +
                                    "unit ${"%.3f".format(up)} cost ${CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)} " +
                                    "vol ${formatVolume(e.gallons, data.volumeLabel)}",
                            )
                        }
                    }
                    ReportsLabShare.shareText(data.context, "Fuel & cost trends", body)
                }) { Text("Share TEXT") }
                OutlinedButton(onClick = {
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
                    ReportsLabShare.shareCsv(data.context, "lab_cost_trends.csv", sb.toString(), "Cost trends CSV")
                }) { Text("Share CSV") }
            }
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
        LabUnitPriceLineChart(chartY)
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
