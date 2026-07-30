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
fun ReportsLabMonthlyCostsScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    val fillFuel = remember(data.fuel) { data.fuel.withoutTripStarts() }
    val buckets = remember(fillFuel, data.expenses, data.defaultStored) {
        monthlyCostBuckets(fillFuel, data.expenses, data.defaultStored)
    }
    val chartCurrency = remember(buckets, data.defaultStored) {
        val keys = buckets.flatMap { it.fuelByCurrency.keys + it.otherByCurrency.keys }.toSet()
        when {
            data.defaultStored in keys -> data.defaultStored
            keys.isNotEmpty() -> keys.first()
            else -> data.defaultStored
        }
    }
    val fuelSeries = buckets.map { (it.fuelByCurrency[chartCurrency] ?: 0.0).toFloat() }
    val otherSeries = buckets.map { (it.otherByCurrency[chartCurrency] ?: 0.0).toFloat() }

    ReportsLabScreenScaffold(
        title = "Monthly costs",
        subtitle = "Fuel vs other expenses by calendar month. Mixed currency: per-currency lines (no FX).",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareRow = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val body = buildString {
                        appendLine("Vehicle Expenses — Monthly costs (experimental)")
                        appendLine("Period: ${periodLabel(data.filter)}")
                        appendLine("Vehicle: ${data.filterVehicleLabel()}")
                        buckets.forEach { b ->
                            appendLine("--- ${b.key} ---")
                            appendLine("  Fuel: ${CurrencyCodes.formatAggregateSum(b.fuelByCurrency, data.defaultSymbol)}")
                            appendLine("  Other: ${CurrencyCodes.formatAggregateSum(b.otherByCurrency, data.defaultSymbol)}")
                            val total = (b.fuelByCurrency.keys + b.otherByCurrency.keys).associateWith { c ->
                                (b.fuelByCurrency[c] ?: 0.0) + (b.otherByCurrency[c] ?: 0.0)
                            }
                            appendLine("  Total: ${CurrencyCodes.formatAggregateSum(total, data.defaultSymbol)}")
                        }
                    }
                    ReportsLabShare.shareText(data.context, "Monthly costs", body)
                }) { Text("Share TEXT") }
                OutlinedButton(onClick = {
                    val sb = StringBuilder("month,kind,currency,amount\n")
                    buckets.forEach { b ->
                        b.fuelByCurrency.forEach { (c, a) ->
                            sb.append("${b.key},fuel,${c.ifBlank { data.defaultStored }},$a\n")
                        }
                        b.otherByCurrency.forEach { (c, a) ->
                            sb.append("${b.key},other,${c.ifBlank { data.defaultStored }},$a\n")
                        }
                    }
                    ReportsLabShare.shareCsv(data.context, "lab_monthly.csv", sb.toString(), "Monthly costs CSV")
                }) { Text("Share CSV") }
            }
        },
    ) {
        if (buckets.isEmpty()) {
            ReportsLabEmpty("No costs in this filter.")
            return@ReportsLabScreenScaffold
        }
        LabMonthlyBarsChart(
            fuelAmounts = fuelSeries,
            otherAmounts = otherSeries,
            caption = "Bars: fuel vs other for currency $chartCurrency (no FX conversion).",
        )
        Spacer(Modifier.height(8.dp))
        buckets.asReversed().forEach { b ->
            val total = (b.fuelByCurrency.keys + b.otherByCurrency.keys).associateWith { c ->
                (b.fuelByCurrency[c] ?: 0.0) + (b.otherByCurrency[c] ?: 0.0)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(b.key, style = MaterialTheme.typography.titleSmall)
                Text("Fuel: ${CurrencyCodes.formatAggregateSum(b.fuelByCurrency, data.defaultSymbol)}")
                Text("Other: ${CurrencyCodes.formatAggregateSum(b.otherByCurrency, data.defaultSymbol)}")
                Text("Total: ${CurrencyCodes.formatAggregateSum(total, data.defaultSymbol)}")
            }
        }
    }
}
