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
fun ReportsLabExpenseCategoriesScreen(navController: NavHostController) {
    val data = rememberLabReportData(LabVehicleMembership.EXPENSE)
    val isEach = data.filter.vehicleMode == LabVehicleMode.EACH

    val byCat = remember(data.expenses, data.defaultStored) {
        categoryTotals(data.expenses, data.defaultStored)
    }
    val chartCurrency = remember(byCat, data.defaultStored) {
        val keys = byCat.values.flatMap { it.keys }.toSet()
        when {
            data.defaultStored in keys -> data.defaultStored
            keys.isNotEmpty() -> keys.first()
            else -> data.defaultStored
        }
    }
    val sortedCats = byCat.entries.sortedByDescending { (_, m) -> m[chartCurrency] ?: m.values.sum() }
    val chartAmounts = sortedCats.map { (_, m) -> (m[chartCurrency] ?: 0.0).toFloat() }
    val catLabels = sortedCats.map { it.key }

    // Each: categories on X, one series per vehicle
    val eachSeries = remember(
        isEach, data.expenses, data.defaultStored, chartCurrency, catLabels, data.vehicles,
    ) {
        if (!isEach || catLabels.isEmpty()) emptyMap()
        else {
            val byVehicle = data.expenses.groupBy { it.vehicleId }
            byVehicle.entries
                .sortedBy { (vid, _) -> data.vehicleName(vid) }
                .associate { (vid, exps) ->
                    val totals = categoryTotals(exps, data.defaultStored)
                    data.vehicleName(vid) to catLabels.map { cat ->
                        (totals[cat]?.get(chartCurrency) ?: 0.0).toFloat()
                    }
                }
        }
    }
    val eachByVehicleTotals = remember(isEach, data.expenses, data.defaultStored) {
        if (!isEach) emptyMap()
        else {
            data.expenses.groupBy { it.vehicleId }
                .entries
                .sortedBy { (vid, _) -> data.vehicleName(vid) }
                .associate { (vid, exps) ->
                    data.vehicleName(vid) to categoryTotals(exps, data.defaultStored)
                }
        }
    }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_expenses_by_category),
        infoText = "Category totals for the filtered period. Chart uses one currency series (caption). " +
            stringResource(R.string.reports_each_vehicle_multi_series_categories_on_x_one_se),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Expenses by category")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    if (isEach) {
                        eachByVehicleTotals.forEach { (vName, cats) ->
                            appendLine("--- $vName ---")
                            cats.forEach { (cat, m) ->
                                appendLine("  $cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
                            }
                        }
                    } else {
                        sortedCats.forEach { (cat, m) ->
                            appendLine("$cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
                        }
                    }
                    appendLine("--- rows ---")
                    data.expenses.sortedByDescending { it.date }.forEach { e ->
                        appendLine(
                            "${formatLabDate(e.date)} ${e.category} " +
                                CurrencyCodes.formatAmount(e.amount, e.currency, data.defaultSymbol) +
                                " ${e.description.take(40)}",
                        )
                    }
                }
            }
            ReportsLabShareActions(
                subject = "Expenses by category",
                textBody = buildText,
                csvFileName = "lab_expenses.csv",
                csvBody = {
                    val sb = StringBuilder("section,category,date,amount,currency,description,vehicle\n")
                    if (isEach) {
                        eachByVehicleTotals.forEach { (vName, cats) ->
                            cats.forEach { (cat, m) ->
                                m.forEach { (c, a) ->
                                    sb.append(
                                        "total,${ReportsLabShare.csvEscape(cat)},,$a," +
                                            "${c.ifBlank { data.defaultStored }},," +
                                            "${ReportsLabShare.csvEscape(vName)}\n",
                                    )
                                }
                            }
                        }
                    } else {
                        sortedCats.forEach { (cat, m) ->
                            m.forEach { (c, a) ->
                                sb.append(
                                    "total,${ReportsLabShare.csvEscape(cat)},,$a," +
                                        "${c.ifBlank { data.defaultStored }},,\n",
                                )
                            }
                        }
                    }
                    data.expenses.forEach { e ->
                        sb.append(
                            listOf(
                                "row",
                                ReportsLabShare.csvEscape(e.category.ifBlank { "Other" }),
                                formatLabDate(e.date),
                                "%.4f".format(e.amount),
                                e.currency.ifBlank { data.defaultStored },
                                ReportsLabShare.csvEscape(e.description),
                                ReportsLabShare.csvEscape(data.vehicleName(e.vehicleId)),
                            ).joinToString(","),
                        ).append('\n')
                    }
                    sb.toString()
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Expenses by category", buildText()) },
            )
        },
    ) {
        if (data.expenses.isEmpty()) {
            ReportsLabEmpty("No expenses in this filter.")
            return@ReportsLabScreenScaffold
        }
        if (isEach) {
            LabMultiSeriesIndexChart(
                series = eachSeries,
                xLabels = catLabels,
                caption = "Category amounts per vehicle (currency $chartCurrency, no FX).",
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reports_per_vehicle_category_totals), style = MaterialTheme.typography.titleSmall)
            eachByVehicleTotals.forEach { (vName, cats) ->
                Text(vName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                cats.entries
                    .sortedByDescending { (_, m) -> m[chartCurrency] ?: m.values.sum() }
                    .forEach { (cat, m) ->
                        Text(
                            "  $cat · ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
            }
        } else {
            LabCategoryBarsChart(
                amounts = chartAmounts,
                categoryLabels = catLabels,
                caption = "Category bars for currency $chartCurrency (no FX). Other currencies listed in tables.",
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reports_category_totals), style = MaterialTheme.typography.titleSmall)
            sortedCats.forEach { (cat, m) ->
                Text("$cat · ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reports_all_expenses_in_period), style = MaterialTheme.typography.titleSmall)
        data.expenses.sortedByDescending { it.date }.forEach { e ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${formatLabDate(e.date)} · ${e.category.ifBlank { "Other" }} · ${data.vehicleName(e.vehicleId)}")
                Text(
                    "${CurrencyCodes.formatAmount(e.amount, e.currency, data.defaultSymbol)} · ${e.description.ifBlank { "(no description)" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
