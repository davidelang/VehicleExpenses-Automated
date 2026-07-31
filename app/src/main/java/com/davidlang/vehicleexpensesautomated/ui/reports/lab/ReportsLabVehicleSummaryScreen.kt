package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.reports.LastFullFillLegsBlock
import com.davidlang.vehicleexpensesautomated.ui.reports.lastFullFillLegsShareLines
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes

@Composable
fun ReportsLabVehicleSummaryScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    var includeVinInShare by remember { mutableStateOf(false) }

    val targets: List<Vehicle?> = remember(
        data.filter.vehicleMode,
        data.filter.vehicleId,
        data.vehicles,
    ) {
        when (data.filter.vehicleMode) {
            LabVehicleMode.ALL -> listOf(null) // combined all-vehicles pack
            LabVehicleMode.EACH -> data.vehicles.map { it } // stacked per vehicle
            LabVehicleMode.SINGLE -> {
                val id = data.filter.vehicleId
                listOf(
                    data.vehicles.firstOrNull { it.id == id }
                        ?: data.allVehicles.firstOrNull { it.id == id },
                )
            }
        }
    }

    fun packForVehicle(v: Vehicle?): String {
        val vid = v?.id ?: data.filter.vehicleId
        val fuelAll = if (vid != null) data.fuel.filter { it.vehicleId == vid } else data.fuel
        val fills = fuelAll.withoutTripStarts()
        val exp = if (vid != null) data.expenses.filter { it.vehicleId == vid } else data.expenses
        val legs = allValidLegsChrono(fuelAll, data.defaultStored)
        val (minO, maxO) = odometerRange(fuelAll)
        val dist = if (minO != null && maxO != null && maxO >= minO) maxO - minO else null
        val fuelCost = CurrencyCodes.sumByCurrency(fuelAll, data.defaultStored, { it.currency }, { it.cost })
        val expCost = CurrencyCodes.sumByCurrency(exp, data.defaultStored, { it.currency }, { it.amount })
        val topCats = categoryTotals(exp, data.defaultStored).entries
            .sortedByDescending { it.value.values.sum() }
            .take(5)
        val dpm = dollarsPerMile(fuelAll, exp, data.defaultStored)
        return buildString {
            appendLine("Vehicle Expenses — Vehicle summary")
            appendLine("Generated: ${formatLabDateTime(System.currentTimeMillis())}")
            appendLine("Period: ${periodLabel(data.filter)}")
            val identity = buildList {
                v?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
                listOfNotNull(v?.make, v?.model).joinToString(" ").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                v?.year?.let { add(it.toString()) }
                v?.licensePlate?.takeIf { it.isNotBlank() }?.let { add("plate $it") }
            }.joinToString(" · ").ifBlank { data.filterVehicleLabel() }
            appendLine("Vehicle: $identity")
            if (includeVinInShare && !v?.vin.isNullOrBlank()) {
                appendLine("VIN: ${v?.vin}")
            }
            appendLine()
            appendLine(
                "Odometer: " + when {
                    minO != null && maxO != null ->
                        "$minO → $maxO" +
                        (dist?.let {
                            " (≈ ${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.distanceDeltaLabel(it)})"
                        } ?: "")
                    else -> "n/a"
                },
            )
            appendLine(
                "Fills: ${fills.size} (${fills.count { it.isPartialFill }} marked partial) · " +
                    "Volume: ${formatVolume(fuelAll.sumOf { it.gallons }, data.volumeLabel)}",
            )
            appendLine("Fuel cost: ${CurrencyCodes.formatAggregateSum(fuelCost, data.defaultSymbol)}")
            appendLine(
                "Last ${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()}: " +
                    "${formatMpg(lastMpg(legs))} · Avg " +
                    "${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()}: " +
                    "${formatMpg(avgMpg(legs))} · " +
                    "${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.costPerDistanceLabel()}: " +
                    if (dpm == null) "n/a" else "%.3f".format(dpm),
            )
            appendLine("(Full-fill and economyIgnored rules apply; trip starts excluded from fill counts.)")
            appendLine("Expenses: ${CurrencyCodes.formatAggregateSum(expCost, data.defaultSymbol)}")
            topCats.forEach { (cat, m) ->
                appendLine("  $cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
            }
            appendLine()
            appendLine("Last 5 full fills:")
            lastFullFillLegsShareLines(legs, data.volumeLabel, data.defaultSymbol).forEach {
                appendLine(it)
            }
            appendLine("Recent expenses:")
            exp.sortedByDescending { it.date }.take(5).forEach { e ->
                appendLine(
                    "  ${formatLabDate(e.date)} ${e.category} " +
                        CurrencyCodes.formatAmount(e.amount, e.currency, data.defaultSymbol),
                )
            }
            appendLine()
        }
    }

    fun csvPack(): String {
        val sb = StringBuilder("section,key,value\n")
        fun row(section: String, key: String, value: String) {
            sb.append(
                listOf(section, ReportsLabShare.csvEscape(key), ReportsLabShare.csvEscape(value)).joinToString(","),
            ).append('\n')
        }
        row("meta", "generated", formatLabDateTime(System.currentTimeMillis()))
        row("meta", "period", periodLabel(data.filter))
        row("meta", "vehicle_filter", data.filterVehicleLabel())
        targets.forEach { v ->
            val vid = v?.id
            val fuelAll = if (vid != null) data.fuel.filter { it.vehicleId == vid } else data.fuel
            val fills = fuelAll.withoutTripStarts()
            val exp = if (vid != null) data.expenses.filter { it.vehicleId == vid } else data.expenses
            val prefix = v?.name ?: "all"
            row("identity", "name", v?.name.orEmpty())
            row("identity", "make", v?.make.orEmpty())
            row("identity", "model", v?.model.orEmpty())
            row("identity", "year", v?.year?.toString().orEmpty())
            row("identity", "plate", v?.licensePlate.orEmpty())
            if (includeVinInShare) row("identity", "vin", v?.vin.orEmpty())
            val (minO, maxO) = odometerRange(fuelAll)
            row("odo", "min", minO?.toString() ?: "")
            row("odo", "max", maxO?.toString() ?: "")
            row("fuel", "count", fills.size.toString())
            row("fuel", "partial", fills.count { it.isPartialFill }.toString())
            row("fuel", "volume", "%.4f".format(fuelAll.sumOf { it.gallons }))
            CurrencyCodes.sumByCurrency(fuelAll, data.defaultStored, { it.currency }, { it.cost })
                .forEach { (c, a) -> row("fuel_cost", c, a.toString()) }
            val legs = allValidLegsChrono(fuelAll, data.defaultStored)
            row("economy", "last_mpg", lastMpg(legs)?.toString() ?: "")
            row("economy", "avg_mpg", avgMpg(legs)?.toString() ?: "")
            row("economy", "dpm", dollarsPerMile(fuelAll, exp, data.defaultStored)?.toString() ?: "")
            CurrencyCodes.sumByCurrency(exp, data.defaultStored, { it.currency }, { it.amount })
                .forEach { (c, a) -> row("expense_total", c, a.toString()) }
            excludeMpgOutliers(legs).asReversed().take(5).forEach { leg ->
                sb.append(
                    listOf(
                        "full_fill_leg",
                        prefix,
                        formatLabDate(leg.endFill.timestamp),
                        leg.endFill.odometer.toString(),
                        "%.4f".format(leg.mpg),
                        "%.4f".format(leg.sumVol),
                        leg.miles.toString(),
                    ).joinToString(","),
                ).append('\n')
            }
            exp.sortedByDescending { it.date }.forEach { e ->
                sb.append(
                    listOf(
                        "expense_row",
                        prefix,
                        formatLabDate(e.date),
                        ReportsLabShare.csvEscape(e.category),
                        "%.4f".format(e.amount),
                        e.currency.ifBlank { data.defaultStored },
                    ).joinToString(","),
                ).append('\n')
            }
        }
        return sb.toString()
    }

    ReportsLabScreenScaffold(
        title = "Vehicle summary",
        infoText = "A shareable history pack for this vehicle and period. " +
            "Toggle Include VIN below before sharing if needed (default off).",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = { targets.joinToString("\n") { packForVehicle(it) } }
            ReportsLabShareActions(
                subject = "Vehicle summary",
                textBody = buildText,
                csvFileName = "lab_vehicle_summary.csv",
                csvBody = { csvPack() },
                pdfBody = { ReportsLabPdf.fromPlainText("Vehicle summary", buildText()) },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeVinInShare, onCheckedChange = { includeVinInShare = it })
            Text("Include VIN in share (default off)", style = MaterialTheme.typography.bodySmall)
        }
        if (data.fuel.isEmpty() && data.expenses.isEmpty() && targets.all { it == null }) {
            ReportsLabEmpty("No data for this filter.")
            return@ReportsLabScreenScaffold
        }
        targets.forEach { v ->
            VehicleSummarySection(data = data, vehicle = v, includeVinOnScreen = true)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VehicleSummarySection(
    data: LabReportData,
    vehicle: Vehicle?,
    includeVinOnScreen: Boolean,
) {
    val vid = vehicle?.id
    val fuelAll = if (vid != null) data.fuel.filter { it.vehicleId == vid } else data.fuel
    val fills = fuelAll.withoutTripStarts()
    val exp = if (vid != null) data.expenses.filter { it.vehicleId == vid } else data.expenses
    val legs = allValidLegsChrono(fuelAll, data.defaultStored)
    val (minO, maxO) = odometerRange(fuelAll)
    val dist = if (minO != null && maxO != null && maxO >= minO) maxO - minO else null
    val fuelCost = CurrencyCodes.sumByCurrency(fuelAll, data.defaultStored, { it.currency }, { it.cost })
    val expCost = CurrencyCodes.sumByCurrency(exp, data.defaultStored, { it.currency }, { it.amount })
    val topCats = categoryTotals(exp, data.defaultStored).entries
        .sortedByDescending { it.value.values.sum() }
        .take(5)
    val dpm = dollarsPerMile(fuelAll, exp, data.defaultStored)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vehicle summary", style = MaterialTheme.typography.titleMedium)
            Text("Generated: ${formatLabDateTime(System.currentTimeMillis())}", style = MaterialTheme.typography.labelSmall)
            Text("Period: ${periodLabel(data.filter)}")
            Text(
                buildList {
                    vehicle?.name?.let { add(it) }
                    listOfNotNull(vehicle?.make, vehicle?.model).joinToString(" ").trim()
                        .takeIf { it.isNotEmpty() }?.let { add(it) }
                    vehicle?.year?.let { add(it.toString()) }
                    vehicle?.licensePlate?.takeIf { it.isNotBlank() }?.let { add("plate $it") }
                }.joinToString(" · ").ifBlank { data.filterVehicleLabel() },
                style = MaterialTheme.typography.titleSmall,
            )
            if (includeVinOnScreen && !vehicle?.vin.isNullOrBlank()) {
                Text("VIN: ${vehicle?.vin}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Odometer: " + when {
                    minO != null && maxO != null ->
                        "$minO → $maxO" +
                        (dist?.let {
                            " (≈ ${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.distanceDeltaLabel(it)})"
                        } ?: "")
                    else -> "n/a"
                },
                softWrap = true,
            )
            Text(
                "Fills: ${fills.size} (${fills.count { it.isPartialFill }} partial) · " +
                    formatVolume(fuelAll.sumOf { it.gallons }, data.volumeLabel),
                softWrap = true,
            )
            Text("Fuel: ${CurrencyCodes.formatAggregateSum(fuelCost, data.defaultSymbol)}")
            Text(
                "Last ${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()} " +
                    "${formatMpg(lastMpg(legs))} · Avg " +
                    "${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.economyEfficiencyLabel()} " +
                    "${formatMpg(avgMpg(legs))} · " +
                    "${com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat.costPerDistanceLabel()} " +
                    if (dpm == null) "n/a" else "%.3f".format(dpm),
                softWrap = true,
            )
            Text(
                "Full-fill and economyIgnored rules apply; trip starts excluded from fill counts.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
            Text("Expenses: ${CurrencyCodes.formatAggregateSum(expCost, data.defaultSymbol)}")
            topCats.forEach { (cat, m) ->
                Text("  $cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}", style = MaterialTheme.typography.bodySmall)
            }
            LastFullFillLegsBlock(
                legsChrono = legs,
                volumeUnitLabel = data.volumeLabel,
                defaultSymbol = data.defaultSymbol,
            )
            Text("Last 5 expenses", style = MaterialTheme.typography.titleSmall)
            exp.sortedByDescending { it.date }.take(5).forEach { e ->
                Text(
                    "${formatLabDate(e.date)} ${e.category} " +
                        CurrencyCodes.formatAmount(e.amount, e.currency, data.defaultSymbol),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (exp.isEmpty()) Text("  (none)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
