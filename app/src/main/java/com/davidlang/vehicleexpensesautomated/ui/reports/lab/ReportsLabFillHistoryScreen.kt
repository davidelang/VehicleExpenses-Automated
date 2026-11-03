package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.AdaptiveItemGrid
import com.davidlang.vehicleexpensesautomated.ui.components.TappableCard
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

@Composable
fun ReportsLabFillHistoryScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    val rows = remember(data.fuel) {
        data.fuel.withoutTripStarts().sortedByDescending { it.timestamp }
    }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_fill_history),
        infoText = stringResource(R.string.fill_chronological_fills_for_current_filters_tr) +
            stringResource(R.string.fill_each_vehicle_uses_the_same_multi_vehicle_l) +
            stringResource(R.string.reports_tap_a_row_to_edit),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine(AppStrings.get(R.string.fill_vehicle_expenses_fill_history))
                    appendLine(AppStrings.get(R.string.reports_period_label, periodLabel(data.filter)))
                    appendLine(AppStrings.get(R.string.reports_vehicle_label, data.filterVehicleLabel()))
                    appendLine(AppStrings.get(R.string.fill_count_1_s, rows.size))
                    rows.forEach { e ->
                        val flags = buildList {
                            if (e.isPartialFill) add("partial")
                            if (e.economyIgnored) add("ignored")
                        }.joinToString(",")
                        appendLine(
                            "${formatLabDate(e.timestamp)} ${data.vehicleName(e.vehicleId)} " +
                                AppStrings.get(R.string.fill_odo_1_s_2_s, e.odometer, CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)) +
                                "${formatVolume(e.gallons, data.volumeLabel)}" +
                                if (flags.isNotEmpty()) " [$flags]" else "",
                        )
                    }
                }
            }
            ReportsLabShareActions(
                subject = AppStrings.get(R.string.reports_fill_history),
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
                pdfBody = { ReportsLabPdf.fromPlainText(AppStrings.get(R.string.reports_fill_history), buildText()) },
            )
        },
    ) {
        if (rows.isEmpty()) {
            ReportsLabEmpty(stringResource(R.string.reports_no_fills_in_this_filter))
            return@ReportsLabScreenScaffold
        }
        Text(stringResource(R.string.fill_s_1_s_fills, rows.size), style = MaterialTheme.typography.titleSmall)
        AdaptiveItemGrid(items = rows) { e ->
            val flags = buildList {
                if (e.isPartialFill) add("partial")
                if (e.economyIgnored) add("ignored")
            }.joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
            TappableCard(onClick = { navController.navigate("fuel/${e.id}") }) {
                Text("${data.vehicleName(e.vehicleId)} · ${formatLabDate(e.timestamp)}$flags", softWrap = true, maxLines = 2)
                Text(
                    stringResource(R.string.fill_odo_1_s_2_s_2, e.odometer, CurrencyCodes.formatAmount(e.cost, e.currency, data.defaultSymbol)) +
                        formatVolume(e.gallons, data.volumeLabel),
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true,
                    maxLines = 3,
                )
            }
        }
    }
}
