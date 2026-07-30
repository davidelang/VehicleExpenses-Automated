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
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

@Composable
fun ReportsLabEfficiencyScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    val legs = remember(data.fuel, data.defaultStored) {
        allValidLegsChrono(data.fuel, data.defaultStored)
    }
    val displayLegs = remember(legs) { excludeMpgOutliers(legs) }
    val chartY = remember(displayLegs) { displayLegs.map { it.mpg.toFloat() } }

    ReportsLabScreenScaffold(
        title = "Fuel efficiency",
        subtitle = "Full-fill legs use the same economy rules as production Reports (economyIgnored / partial / chain breakers).",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareRow = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val body = buildString {
                        appendLine("Vehicle Expenses — Fuel efficiency (experimental)")
                        appendLine("Period: ${periodLabel(data.filter)}")
                        appendLine("Vehicle: ${data.filterVehicleLabel()}")
                        appendLine(
                            "Last ${UnitFormat.economyEfficiencyLabel()}: ${formatMpg(lastMpg(legs))} · " +
                                "Avg ${UnitFormat.economyEfficiencyLabel()}: ${formatMpg(avgMpg(legs))}",
                        )
                        appendLine("Legs (display-filtered): ${displayLegs.size}")
                        displayLegs.takeLast(50).forEach { leg ->
                            appendLine(
                                "${formatLabDate(leg.endFill.timestamp)} odo ${leg.endFill.odometer} " +
                                    "${UnitFormat.economyEfficiencyLabel()} ${formatMpg(leg.mpg)} " +
                                    "vol ${formatVolume(leg.sumVol, data.volumeLabel)} " +
                                    "cost ${CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, data.defaultSymbol)}",
                            )
                        }
                    }
                    ReportsLabShare.shareText(data.context, "Fuel efficiency", body)
                }) { Text("Share TEXT") }
                OutlinedButton(onClick = {
                    val sb = StringBuilder("date,odo,mpg,volume,cost_summary,miles\n")
                    displayLegs.forEach { leg ->
                        sb.append(
                            listOf(
                                formatLabDate(leg.endFill.timestamp),
                                leg.endFill.odometer.toString(),
                                "%.3f".format(leg.mpg),
                                "%.4f".format(leg.sumVol),
                                ReportsLabShare.csvEscape(
                                    CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, data.defaultSymbol),
                                ),
                                leg.miles.toString(),
                            ).joinToString(","),
                        ).append('\n')
                    }
                    ReportsLabShare.shareCsv(data.context, "lab_efficiency.csv", sb.toString(), "Fuel efficiency CSV")
                }) { Text("Share CSV") }
            }
        },
    ) {
        if (data.fuel.isEmpty()) {
            ReportsLabEmpty("No fills in this filter.")
            return@ReportsLabScreenScaffold
        }
        Text(
            "Last ${UnitFormat.economyEfficiencyLabel()}: ${formatMpg(lastMpg(legs))} · " +
                "Avg ${UnitFormat.economyEfficiencyLabel()}: ${formatMpg(avgMpg(legs))} · Legs: ${displayLegs.size}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Display avg/last exclude MPG outside 5–80 and 3× median outliers (same as production).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabMpgLineChart(chartY)
        Spacer(Modifier.height(8.dp))
        Text("Legs (oldest → newest)", style = MaterialTheme.typography.titleSmall)
        if (displayLegs.isEmpty()) {
            ReportsLabEmpty("No valid full-fill legs in this filter.")
        } else {
            displayLegs.asReversed().forEach { leg ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        "${formatLabDate(leg.endFill.timestamp)} · ${data.vehicleName(leg.endFill.vehicleId)} · odo ${leg.endFill.odometer}",
                    )
                    Text(
                        "${UnitFormat.economyEfficiencyLabel()} ${formatMpg(leg.mpg)} · " +
                            "${formatVolume(leg.sumVol, data.volumeLabel)} · " +
                            CurrencyCodes.formatAggregateSum(leg.sumCostByCurrency, data.defaultSymbol) +
                            " · ${UnitFormat.distanceDeltaLabel(leg.miles)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
