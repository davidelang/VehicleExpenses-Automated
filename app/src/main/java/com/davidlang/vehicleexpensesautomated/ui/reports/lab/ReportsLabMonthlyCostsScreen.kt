package com.davidlang.vehicleexpensesautomated.ui.reports.lab

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
fun ReportsLabMonthlyCostsScreen(navController: NavHostController) {
    val data = rememberLabReportData(LabVehicleMembership.FUEL_OR_EXPENSE)
    val fillFuel = remember(data.fuel) { data.fuel.withoutTripStarts() }
    val isEach = data.filter.vehicleMode == LabVehicleMode.EACH

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
    val monthKeys = buckets.map { it.key }
    val fuelSeries = buckets.map { (it.fuelByCurrency[chartCurrency] ?: 0.0).toFloat() }
    val otherSeries = buckets.map { (it.otherByCurrency[chartCurrency] ?: 0.0).toFloat() }

    // Each: per-vehicle monthly total (fuel+other) aligned to shared month keys
    val eachVehicleSeries = remember(
        isEach, fillFuel, data.expenses, data.defaultStored, chartCurrency, monthKeys, data.vehicles,
    ) {
        if (!isEach || monthKeys.isEmpty()) emptyMap()
        else {
            val fuelByV = fillFuel.groupBy { it.vehicleId }
            val expByV = data.expenses.groupBy { it.vehicleId }
            val vids = (fuelByV.keys + expByV.keys).sorted()
            vids.associate { vid ->
                val vBuckets = monthlyCostBuckets(
                    fuelByV[vid].orEmpty(),
                    expByV[vid].orEmpty(),
                    data.defaultStored,
                ).associateBy { it.key }
                data.vehicleName(vid) to monthKeys.map { key ->
                    val b = vBuckets[key] ?: return@map 0f
                    (
                        (b.fuelByCurrency[chartCurrency] ?: 0.0) +
                            (b.otherByCurrency[chartCurrency] ?: 0.0)
                        ).toFloat()
                }
            }
        }
    }

    ReportsLabScreenScaffold(
        title = "Monthly costs",
        infoText = "Fuel vs other expenses by calendar month. Mixed currency: per-currency lines (no FX). " +
            "Each vehicle = one series of monthly total cost per vehicle (month labels on X).",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildString {
                    appendLine("Vehicle Expenses — Monthly costs")
                    appendLine("Period: ${periodLabel(data.filter)}")
                    appendLine("Vehicle: ${data.filterVehicleLabel()}")
                    if (isEach) {
                        eachVehicleSeries.forEach { (vName, amounts) ->
                            appendLine("--- $vName ---")
                            monthKeys.forEachIndexed { i, key ->
                                appendLine("  $key: ${"%.2f".format(amounts.getOrElse(i) { 0f })} $chartCurrency")
                            }
                        }
                    } else {
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
                }
            }
            ReportsLabShareActions(
                subject = "Monthly costs",
                textBody = buildText,
                csvFileName = "lab_monthly.csv",
                csvBody = {
                    val sb = StringBuilder()
                    if (isEach) {
                        sb.append("month,vehicle,amount,currency\n")
                        eachVehicleSeries.forEach { (vName, amounts) ->
                            monthKeys.forEachIndexed { i, key ->
                                sb.append(
                                    "$key,${ReportsLabShare.csvEscape(vName)}," +
                                        "${amounts.getOrElse(i) { 0f }},$chartCurrency\n",
                                )
                            }
                        }
                    } else {
                        sb.append("month,kind,currency,amount\n")
                        buckets.forEach { b ->
                            b.fuelByCurrency.forEach { (c, a) ->
                                sb.append("${b.key},fuel,${c.ifBlank { data.defaultStored }},$a\n")
                            }
                            b.otherByCurrency.forEach { (c, a) ->
                                sb.append("${b.key},other,${c.ifBlank { data.defaultStored }},$a\n")
                            }
                        }
                    }
                    sb.toString()
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Monthly costs", buildText()) },
            )
        },
    ) {
        if (buckets.isEmpty()) {
            ReportsLabEmpty("No costs in this filter.")
            return@ReportsLabScreenScaffold
        }
        if (isEach) {
            LabMultiSeriesIndexChart(
                series = eachVehicleSeries,
                xLabels = monthKeys,
                caption = "Monthly total cost per vehicle (currency $chartCurrency, no FX).",
            )
            Spacer(Modifier.height(8.dp))
            // Detail: month → vehicle lines (E-M3)
            monthKeys.asReversed().forEach { key ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(key, style = MaterialTheme.typography.titleSmall)
                    eachVehicleSeries.forEach { (vName, amounts) ->
                        val i = monthKeys.indexOf(key)
                        val amt = amounts.getOrElse(i) { 0f }
                        if (amt != 0f) {
                            Text(
                                "  $vName · ${"%.2f".format(amt)} $chartCurrency",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        } else {
            LabMonthlyBarsChart(
                fuelAmounts = fuelSeries,
                otherAmounts = otherSeries,
                monthKeys = monthKeys,
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
}
