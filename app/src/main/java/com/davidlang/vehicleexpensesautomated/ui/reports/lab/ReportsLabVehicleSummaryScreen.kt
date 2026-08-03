package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.ui.reports.LastFullFillLegsBlock
import com.davidlang.vehicleexpensesautomated.ui.reports.lastFullFillLegsShareLines
import com.davidlang.vehicleexpensesautomated.ui.util.CurrencyCodes
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

@Composable
fun ReportsLabVehicleSummaryScreen(navController: NavHostController) {
    val context = LocalContext.current
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
            appendLine("Vehicle Expenses — ${context.getString(R.string.reports_vehicle_summary)}")
            appendLine(
                context.getString(R.string.reports_generated_at, formatLabDateTime(System.currentTimeMillis())),
            )
            appendLine(context.getString(R.string.reports_period_label, periodLabel(data.filter)))
            val identity = buildList {
                v?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
                listOfNotNull(v?.make, v?.model).joinToString(" ").trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                v?.year?.let { add(it.toString()) }
                v?.licensePlate?.takeIf { it.isNotBlank() }?.let {
                    add(context.getString(R.string.reports_plate_label, it))
                }
            }.joinToString(" · ").ifBlank { data.filterVehicleLabel() }
            appendLine("Vehicle: $identity")
            if (includeVinInShare && !v?.vin.isNullOrBlank()) {
                appendLine(context.getString(R.string.reports_vin_label, v?.vin.orEmpty()))
            }
            appendLine()
            val odoText = when {
                minO != null && maxO != null ->
                    "$minO → $maxO" +
                        (dist?.let {
                            " (≈ ${UnitFormat.distanceDeltaLabel(it, context)})"
                        } ?: "")
                else -> context.getString(R.string.reports_odometer_na)
            }
            appendLine(context.getString(R.string.reports_odometer_range, odoText))
            appendLine(
                context.getString(
                    R.string.reports_fills_partial_line,
                    fills.size,
                    fills.count { it.isPartialFill },
                    formatVolume(fuelAll.sumOf { it.gallons }, data.volumeLabel),
                ),
            )
            appendLine(
                context.getString(
                    R.string.reports_fuel_amount,
                    CurrencyCodes.formatAggregateSum(fuelCost, data.defaultSymbol),
                ),
            )
            appendLine(
                context.getString(
                    R.string.reports_last_avg_dpm_line,
                    UnitFormat.economyEfficiencyLabel(context),
                    formatMpg(lastMpg(legs)),
                    UnitFormat.economyEfficiencyLabel(context),
                    formatMpg(avgMpg(legs)),
                    UnitFormat.costPerDistanceLabel(context),
                    if (dpm == null) {
                        context.getString(R.string.reports_odometer_na)
                    } else {
                        "%.3f".format(dpm)
                    },
                ),
            )
            appendLine(context.getString(R.string.reports_full_fill_and_economyignored_rules_apply_trip_st))
            appendLine(
                context.getString(
                    R.string.reports_expenses_amount,
                    CurrencyCodes.formatAggregateSum(expCost, data.defaultSymbol),
                ),
            )
            topCats.forEach { (cat, m) ->
                appendLine("  $cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}")
            }
            appendLine()
            appendLine(context.getString(R.string.reports_last_5_full_fills) + ":")
            lastFullFillLegsShareLines(legs, data.volumeLabel, data.defaultSymbol, efficiencyLabel = UnitFormat.economyEfficiencyLabel(context)).forEach {
                appendLine(it)
            }
            appendLine(context.getString(R.string.reports_last_5_expenses) + ":")
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
        title = stringResource(R.string.reports_vehicle_summary),
        infoText = "A shareable history pack for this vehicle and period. " +
            stringResource(R.string.reports_toggle_include_vin_below_before_sharing_if_neede),
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
            Text(stringResource(R.string.reports_include_vin_in_share_default_off), style = MaterialTheme.typography.bodySmall)
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
    val context = LocalContext.current
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
            Text(stringResource(R.string.reports_vehicle_summary), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.reports_generated_at, formatLabDateTime(System.currentTimeMillis())),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(stringResource(R.string.reports_period_label, periodLabel(data.filter)))
            Text(
                buildList {
                    vehicle?.name?.let { add(it) }
                    listOfNotNull(vehicle?.make, vehicle?.model).joinToString(" ").trim()
                        .takeIf { it.isNotEmpty() }?.let { add(it) }
                    vehicle?.year?.let { add(it.toString()) }
                    vehicle?.licensePlate?.takeIf { it.isNotBlank() }?.let {
                        add(context.getString(R.string.reports_plate_label, it))
                    }
                }.joinToString(" · ").ifBlank { data.filterVehicleLabel() },
                style = MaterialTheme.typography.titleSmall,
            )
            if (includeVinOnScreen && !vehicle?.vin.isNullOrBlank()) {
                Text(
                    stringResource(R.string.reports_vin_label, vehicle?.vin.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val odoText = when {
                minO != null && maxO != null ->
                    "$minO → $maxO" +
                        (dist?.let {
                            " (≈ ${UnitFormat.distanceDeltaLabel(it, context)})"
                        } ?: "")
                else -> stringResource(R.string.reports_odometer_na)
            }
            Text(stringResource(R.string.reports_odometer_range, odoText), softWrap = true)
            Text(
                stringResource(
                    R.string.reports_fills_partial_line,
                    fills.size,
                    fills.count { it.isPartialFill },
                    formatVolume(fuelAll.sumOf { it.gallons }, data.volumeLabel),
                ),
                softWrap = true,
            )
            Text(
                stringResource(
                    R.string.reports_fuel_amount,
                    CurrencyCodes.formatAggregateSum(fuelCost, data.defaultSymbol),
                ),
            )
            Text(
                stringResource(
                    R.string.reports_last_avg_dpm_line,
                    UnitFormat.economyEfficiencyLabel(context),
                    formatMpg(lastMpg(legs)),
                    UnitFormat.economyEfficiencyLabel(context),
                    formatMpg(avgMpg(legs)),
                    UnitFormat.costPerDistanceLabel(context),
                    if (dpm == null) stringResource(R.string.reports_odometer_na) else "%.3f".format(dpm),
                ),
                softWrap = true,
            )
            Text(stringResource(R.string.reports_full_fill_and_economyignored_rules_apply_trip_st),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
            Text(
                stringResource(
                    R.string.reports_expenses_amount,
                    CurrencyCodes.formatAggregateSum(expCost, data.defaultSymbol),
                ),
            )
            topCats.forEach { (cat, m) ->
                Text("  $cat: ${CurrencyCodes.formatAggregateSum(m, data.defaultSymbol)}", style = MaterialTheme.typography.bodySmall)
            }
            LastFullFillLegsBlock(
                legsChrono = legs,
                volumeUnitLabel = data.volumeLabel,
                defaultSymbol = data.defaultSymbol,
            )
            Text(stringResource(R.string.reports_last_5_expenses), style = MaterialTheme.typography.titleSmall)
            exp.sortedByDescending { it.date }.take(5).forEach { e ->
                Text(
                    "${formatLabDate(e.date)} ${e.category} " +
                        CurrencyCodes.formatAmount(e.amount, e.currency, data.defaultSymbol),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (exp.isEmpty()) Text(stringResource(R.string.reports_none), style = MaterialTheme.typography.bodySmall)
        }
    }
}
