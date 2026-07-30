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
fun ReportsLabExpenseCategoriesScreen(navController: NavHostController) {
    val data = rememberLabReportData()
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

    ReportsLabScreenScaffold(
        title = "Expenses by category",
        subtitle = "Category totals for the filtered period. Chart uses one currency series (caption).",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareRow = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val body = buildString {
                        appendLine("Vehicle Expenses — Expenses by category (experimental)")
                        appendLine("Period: ${periodLabel(data.filter)}")
                        appendLine("Vehicle: ${data.filterVehicleLabel()}")
                        sortedCats.forEach { (cat, m) ->
                            appendLine("$cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
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
                    ReportsLabShare.shareText(data.context, "Expenses by category", body)
                }) { Text("Share TEXT") }
                OutlinedButton(onClick = {
                    val sb = StringBuilder("section,category,date,amount,currency,description\n")
                    sortedCats.forEach { (cat, m) ->
                        m.forEach { (c, a) ->
                            sb.append("total,${ReportsLabShare.csvEscape(cat)},,$a,${c.ifBlank { data.defaultStored }},\n")
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
                            ).joinToString(","),
                        ).append('\n')
                    }
                    ReportsLabShare.shareCsv(data.context, "lab_expenses.csv", sb.toString(), "Expenses CSV")
                }) { Text("Share CSV") }
            }
        },
    ) {
        if (data.expenses.isEmpty()) {
            ReportsLabEmpty("No expenses in this filter.")
            return@ReportsLabScreenScaffold
        }
        LabCategoryBarsChart(
            amounts = chartAmounts,
            caption = "Category bars for currency $chartCurrency (no FX). Other currencies listed in tables.",
        )
        Spacer(Modifier.height(8.dp))
        Text("Category totals", style = MaterialTheme.typography.titleSmall)
        sortedCats.forEach { (cat, m) ->
            Text("$cat · ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
        }
        Spacer(Modifier.height(8.dp))
        Text("All expenses in period", style = MaterialTheme.typography.titleSmall)
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
