package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.trip.TripSegments
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

@Composable
fun ReportsLabTripMilesScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    var includePersonalInTotals by remember { mutableStateOf(true) }
    var showZeroLength by remember { mutableStateOf(false) }
    var showPersonalInList by remember { mutableStateOf(true) }

    val (periodStart, periodEnd) = remember(data.filter) { periodBounds(data.filter) }

    // Natural + implicit leading Personal when no start before period (TR1–TR2)
    val baseSegments = remember(
        data.allFuel,
        data.filter.vehicleMode,
        data.filter.vehicleId,
        periodStart,
        periodEnd,
    ) {
        val fuel = data.allFuel
        when (data.filter.vehicleMode) {
            LabVehicleMode.SINGLE -> {
                val vid = data.filter.vehicleId
                if (vid != null) {
                    TripSegments.listSegmentsWithImplicitPersonal(vid, fuel, periodStart, periodEnd)
                } else {
                    TripSegments.listAllSegmentsWithImplicitPersonal(fuel, periodStart, periodEnd)
                }
            }
            LabVehicleMode.ALL, LabVehicleMode.EACH ->
                TripSegments.listAllSegmentsWithImplicitPersonal(fuel, periodStart, periodEnd)
        }
    }
    val inPeriod = remember(baseSegments, periodStart, periodEnd) {
        TripSegments.filterForPeriod(baseSegments, periodStart, periodEnd)
    }
    val listSegs = remember(inPeriod, showPersonalInList, showZeroLength) {
        TripSegments.forList(
            inPeriod,
            includePersonal = showPersonalInList,
            showZeroLength = showZeroLength,
        ).sortedByDescending { it.startTimestamp }
    }
    val isEach = data.filter.vehicleMode == LabVehicleMode.EACH
    val milesByType = remember(inPeriod, includePersonalInTotals, showZeroLength) {
        TripSegments.milesByType(
            inPeriod,
            includePersonal = includePersonalInTotals,
            includeZeroLength = showZeroLength,
        )
    }
    // Each: per-vehicle miles by type (X = type, series = vehicle)
    val milesByVehicleAndType = remember(
        isEach, inPeriod, includePersonalInTotals, showZeroLength, data.vehicles,
    ) {
        if (!isEach) emptyMap()
        else {
            inPeriod.groupBy { it.vehicleId }
                .entries
                .sortedBy { (vid, _) -> data.vehicleName(vid) }
                .associate { (vid, segs) ->
                    data.vehicleName(vid) to TripSegments.milesByType(
                        segs,
                        includePersonal = includePersonalInTotals,
                        includeZeroLength = showZeroLength,
                    )
                }
        }
    }
    val eachTypeLabels = remember(milesByVehicleAndType, milesByType) {
        if (!isEach) emptyList()
        else {
            // Prefer global type order from aggregate map; union any extra vehicle-only types
            val ordered = milesByType.keys.toMutableList()
            milesByVehicleAndType.values.forEach { m ->
                m.keys.forEach { t -> if (t !in ordered) ordered += t }
            }
            ordered
        }
    }
    val eachChartSeries = remember(milesByVehicleAndType, eachTypeLabels) {
        milesByVehicleAndType.mapValues { (_, mbt) ->
            eachTypeLabels.map { t -> (mbt[t] ?: 0).toFloat() }
        }
    }
    val totalMiles = milesByType.values.sum()
    val openCount = remember(inPeriod) { TripSegments.openCount(inPeriod) }
    val chartY = remember(milesByType) { milesByType.values.map { it.toFloat() } }
    val chartLabels = remember(milesByType) { milesByType.keys.toList() }
    val leadingCount = remember(inPeriod) { inPeriod.count { it.isImplicitLeading } }

    ReportsLabScreenScaffold(
        title = stringResource(R.string.reports_trip_miles),
        infoText = tripMilesInfoText(),
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildTextShare(
                    data = data,
                    milesByType = milesByType,
                    totalMiles = totalMiles,
                    openCount = openCount,
                    listSegs = listSegs,
                    includePersonalInTotals = includePersonalInTotals,
                    showZeroLength = showZeroLength,
                    leadingCount = leadingCount,
                )
            }
            ReportsLabShareActions(
                subject = AppStrings.get(R.string.reports_trip_miles),
                textBody = buildText,
                csvFileName = "lab_trip_miles.csv",
                csvBody = {
                    buildCsvShare(
                        data = data,
                        milesByType = milesByType,
                        listSegs = listSegs,
                    )
                },
                pdfBody = { ReportsLabPdf.fromPlainText(AppStrings.get(R.string.reports_trip_miles), buildText()) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includePersonalInTotals,
                    onCheckedChange = { includePersonalInTotals = it },
                )
                Text(
                    stringResource(R.string.reports_include_personal_in_mile_totals_incl_implicit_le),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showPersonalInList,
                    onCheckedChange = { showPersonalInList = it },
                )
                Text(
                    stringResource(R.string.reports_show_personal_segments_in_list),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showZeroLength,
                    onCheckedChange = { showZeroLength = it },
                )
                Text(
                    stringResource(R.string.reports_include_zero_length_segments),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(stringResource(R.string.reports_kpis), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reports_total_miles, UnitFormat.distanceDeltaLabel(totalMiles)) +
                if (!includePersonalInTotals) stringResource(R.string.trip_personal_excluded) else "",
            softWrap = true,
            maxLines = 3,
        )
        Text(
            stringResource(R.string.trip_closed_segments_in_list_1_s_open_2_s, listSegs.count { !it.isOpen }, openCount) +
                if (leadingCount > 0) stringResource(R.string.trip_implicit_leading_personal_1_s, leadingCount) else "",
            style = MaterialTheme.typography.bodySmall,
            softWrap = true,
        )
        if (isEach) {
            if (milesByVehicleAndType.isEmpty() || eachTypeLabels.isEmpty()) {
                ReportsLabEmpty(stringResource(R.string.trip_no_closed_trip_miles_for_these_filters_tog))
            } else {
                milesByVehicleAndType.forEach { (vName, mbt) ->
                    Text(vName, style = MaterialTheme.typography.titleSmall, softWrap = true)
                    mbt.forEach { (type, miles) ->
                        Text(
                            "  $type: ${UnitFormat.distanceDeltaLabel(miles)}",
                            style = MaterialTheme.typography.bodyMedium,
                            softWrap = true,
                        )
                    }
                }
                LabMultiSeriesIndexChart(
                    series = eachChartSeries,
                    xLabels = eachTypeLabels,
                    caption = stringResource(R.string.trip_miles_by_type_per_vehicle_1_s, UnitFormat.distanceUnitShortLabel()),
                )
            }
        } else if (milesByType.isEmpty()) {
            ReportsLabEmpty(stringResource(R.string.trip_no_closed_trip_miles_for_these_filters_tog))
        } else {
            milesByType.forEach { (type, miles) ->
                Text(
                    "$type: ${UnitFormat.distanceDeltaLabel(miles)}",
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                )
            }
            LabCategoryBarsChart(
                amounts = chartY,
                categoryLabels = chartLabels,
                caption = stringResource(R.string.trip_miles_by_type_1_s, UnitFormat.distanceUnitShortLabel()),
            )
            if (chartLabels.isNotEmpty()) {
                Text(
                    stringResource(R.string.trip_bar_order) +
                        chartLabels.mapIndexed { i, t ->
                            val m = milesByType[t] ?: 0
                            "${i + 1}. $t (${UnitFormat.distanceDeltaLabel(m)})"
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    softWrap = true,
                    maxLines = 6,
                )
            }
        }

        Text(stringResource(R.string.reports_trip_starts_segments), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reports_chronological_trip_list_separate_from_fuel_histo),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        if (listSegs.isEmpty()) {
            ReportsLabEmpty(stringResource(R.string.trip_no_trip_segments_match_the_list_filters))
        } else {
            listSegs.forEach { seg ->
                val canOpen = !seg.isImplicitLeading && seg.start.id > 0L
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canOpen) {
                                Modifier.clickable { navController.navigate("fuel/${seg.start.id}") }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    val typeLabel = if (seg.isImplicitLeading) {
                        AppStrings.get(R.string.trip_s_1_s_implicit, seg.tripType)
                    } else {
                        seg.tripType
                    }
                    Text(
                        "${data.vehicleName(seg.vehicleId)} · $typeLabel · ${formatLabDateTime(seg.startTimestamp)}" +
                            if (canOpen) AppStrings.get(R.string.trip_tap_to_edit) else "",
                        style = MaterialTheme.typography.titleSmall,
                        softWrap = true,
                        maxLines = 3,
                    )
                    val status = when {
                        seg.isOpen -> AppStrings.get(R.string.trip_open)
                        seg.isZeroLength -> AppStrings.get(R.string.trip_zero_length)
                        seg.isImplicitLeading -> AppStrings.get(R.string.trip_closed_leading_gap)
                        else -> AppStrings.get(R.string.trip_closed)
                    }
                    val milesLabel = if (seg.isOpen) {
                        "n/a"
                    } else {
                        UnitFormat.distanceDeltaLabel(seg.miles)
                    }
                    val odoPart = buildString {
                        append(UnitFormat.odometerReadingLabel(seg.startOdo))
                        if (seg.endOdo != null) {
                            append(" → ")
                            append(UnitFormat.odometerReadingLabel(seg.endOdo!!))
                        } else {
                            append(" → …")
                        }
                    }
                    Text(
                        "$status · $milesLabel · $odoPart",
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = true,
                        maxLines = 4,
                    )
                }
            }
        }
    }
}

private fun tripMilesInfoText(): String =
    "Miles by trip purpose from open-only segments (start → next start). " +
        AppStrings.get(R.string.trip_within_a_report_period_miles_from_the_peri) +
        AppStrings.get(R.string.trip_start_trip_count_as_personal_when_you_neve) +
        AppStrings.get(R.string.trip_same_as_starting_personal_on_day_1_if_a_ve) +
        AppStrings.get(R.string.trip_baseline_last_odo_in_period_also_counts_as) +
        AppStrings.get(R.string.trip_each_vehicle_miles_by_type_series_and_tota) +
        AppStrings.get(R.string.trip_segment_list_is_the_trip_surface_fuel_hist) +
        AppStrings.get(R.string.trip_not_a_tax_form_export_and_use_elsewhere) +
        AppStrings.get(R.string.trip_period_filters_by_segment_start_time_zero) +
        AppStrings.get(R.string.trip_from_totals_by_default)

private fun buildTextShare(
    data: LabReportData,
    milesByType: Map<String, Int>,
    totalMiles: Int,
    openCount: Int,
    listSegs: List<TripSegments.Segment>,
    includePersonalInTotals: Boolean,
    showZeroLength: Boolean,
    leadingCount: Int,
): String = buildString {
    appendLine(AppStrings.get(R.string.trip_vehicle_expenses_trip_miles))
    appendLine(AppStrings.get(R.string.reports_period_label, periodLabel(data.filter)))
    appendLine(AppStrings.get(R.string.reports_vehicle_label, data.filterVehicleLabel()))
    appendLine(
        AppStrings.get(R.string.trip_totals_personal_1_s, if (includePersonalInTotals) "included" else "excluded") +
            AppStrings.get(R.string.trip_zero_length_1_s, if (showZeroLength) "included" else "excluded"),
    )
    if (leadingCount > 0) {
        appendLine(AppStrings.get(R.string.trip_implicit_leading_personal_segments_in_wind, leadingCount))
    }
    appendLine(AppStrings.get(R.string.reports_total_miles, UnitFormat.distanceDeltaLabel(totalMiles)))
    appendLine(AppStrings.get(R.string.trip_open_segments_in_period_by_start_1_s, openCount))
    appendLine()
    appendLine(AppStrings.get(R.string.trip_miles_by_type))
    if (milesByType.isEmpty()) {
        appendLine(AppStrings.get(R.string.reports_none))
    } else {
        milesByType.forEach { (t, m) ->
            appendLine("  $t: ${UnitFormat.distanceDeltaLabel(m)}")
        }
    }
    appendLine()
    appendLine(AppStrings.get(R.string.trip_segments))
    listSegs.forEach { seg ->
        val status = when {
            seg.isOpen -> AppStrings.get(R.string.trip_open)
            seg.isZeroLength -> AppStrings.get(R.string.trip_zero)
            seg.isImplicitLeading -> AppStrings.get(R.string.trip_closed_implicit)
            else -> AppStrings.get(R.string.trip_closed)
        }
        val miles = if (seg.isOpen) "n/a" else UnitFormat.distanceDeltaLabel(seg.miles)
        val endOdo = seg.endOdo?.let { UnitFormat.odometerReadingLabel(it) } ?: "…"
        val type = if (seg.isImplicitLeading) AppStrings.get(R.string.trip_s_1_s_implicit_2, seg.tripType) else seg.tripType
        appendLine(
            "  ${formatLabDate(seg.startTimestamp)} ${data.vehicleName(seg.vehicleId)} " +
                "$type $status $miles " +
                "${UnitFormat.odometerReadingLabel(seg.startOdo)} → $endOdo",
        )
    }
}

private fun buildCsvShare(
    data: LabReportData,
    milesByType: Map<String, Int>,
    listSegs: List<TripSegments.Segment>,
): String {
    val unit = UnitFormat.distanceUnitShortLabel()
    val sb = StringBuilder()
    sb.appendLine("section,key,value,unit")
    sb.appendLine("meta,period,${ReportsLabShare.csvEscape(periodLabel(data.filter))},")
    sb.appendLine("meta,vehicle,${ReportsLabShare.csvEscape(data.filterVehicleLabel())},")
    milesByType.forEach { (t, m) ->
        sb.appendLine(
            "total_by_type,${ReportsLabShare.csvEscape(t)},$m,$unit",
        )
    }
    sb.appendLine(
        "segment_header,date,vehicle,type,status,miles,start_odo,end_odo,implicit_leading,unit",
    )
    listSegs.forEach { seg ->
        val status = when {
            seg.isOpen -> "open"
            seg.isZeroLength -> "zero"
            else -> "closed"
        }
        val miles = if (seg.isOpen) "" else seg.miles.toString()
        val endOdo = seg.endOdo?.toString().orEmpty()
        sb.append(
            listOf(
                "segment",
                formatLabDate(seg.startTimestamp),
                ReportsLabShare.csvEscape(data.vehicleName(seg.vehicleId)),
                ReportsLabShare.csvEscape(seg.tripType),
                status,
                miles,
                seg.startOdo.toString(),
                endOdo,
                if (seg.isImplicitLeading) "1" else "0",
                unit,
            ).joinToString(","),
        ).append('\n')
    }
    return sb.toString()
}
