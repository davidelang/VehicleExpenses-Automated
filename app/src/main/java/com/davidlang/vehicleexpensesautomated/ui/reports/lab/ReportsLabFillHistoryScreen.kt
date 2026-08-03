package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes

@Composable
fun ReportsLabFillHistoryScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    val rows = remember(data.fuel) {
        data.fuel.withoutTripStarts().sortedByDescending { it.timestamp }
    }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_fill_history),
        infoText = "Chronological fills for current filters (trip starts excluded). " +
            "Each vehicle uses the same multi-vehicle list as All (vehicle name on each row). " +
            stringResource(R.string.reports_tap_a_row_to_edit),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Fill history")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    appendLine("Count: ${rows.size}")
                    rows.forEach { e ->
                        val flags = buildList {
                            if (e.isPartialFill) add("partial")
                            if (e.economyIgnored) add("ignored")
                        }.joinToString(",")
                        appendLine(
                            "${formatLabDate(e.timestamp)} ${data.vehicleName(e.vehicleId)} " +
                                "odo ${e.odometer} ${CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)} " +
                                "${formatVolume(e.gallons, data.volumeLabel)}" +
                                if (flags.isNotEmpty()) " [$flags]" else "",
                        )
                    }
                }
            }
            ReportsLabShareActions(
                subject = "Fill history",
                textBody = buildText,
                csvFileName = "lab_fills.csv",
                csvBody = {
                    val sb = StringBuilder("date,vehicle,odo,cost,volume,currency,partial,economy_ignored,notes\n")
                    rows.forEach { e ->
                        sb.append(
                            listOf(
                                formatLabDate(e.timestamp),
                                ReportsLabShare.csvEscape(data.vehicleName(e.vehicleId)),
                                e.odometer.toString(),
                                "%.4f".format(e.cost),
                                "%.4f".format(e.gallons),
                                e.currency.ifBlank { data.defaultStored },
                                e.isPartialFill.toString(),
                                e.economyIgnored.toString(),
                                ReportsLabShare.csvEscape(e.notes.orEmpty()),
                            ).joinToString(","),
                        ).append('\n')
                    }
                    sb.toString()
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Fill history", buildText()) },
            )
        },
    ) {
        if (rows.isEmpty()) {
            ReportsLabEmpty("No fills in this filter.")
            return@ReportsLabScreenScaffold
        }
        Text("${rows.size} fills", style = MaterialTheme.typography.titleSmall)
        AdaptiveItemGrid(items = rows) { e ->
            val flags = buildList {
                if (e.isPartialFill) add("partial")
                if (e.economyIgnored) add("ignored")
            }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
            TappableCard(onClick = { navController.navigate("fuel/${e.id}") }) {
                Text("${data.vehicleName(e.vehicleId)} · ${formatLabDate(e.timestamp)}$flags", softWrap = true, maxLines = 2)
                Text(
                    "odo ${e.odometer} · ${CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)} · " +
                        formatVolume(e.gallons, data.volumeLabel),
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true,
                    maxLines = 3,
                )
            }
        }
    }
}
