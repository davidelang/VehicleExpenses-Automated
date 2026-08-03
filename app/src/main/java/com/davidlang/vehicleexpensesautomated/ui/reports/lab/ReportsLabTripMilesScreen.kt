package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.data.trip.TripSegments
import com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat

@Composable
fun ReportsLabTripMilesScreen(navController: NavHostController) {
    val context = LocalContext.current
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
        infoText = TRIP_MILES_INFO,
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareActions = run {
            val buildText = {
                buildTextShare(context, 
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
                subject = "Trip miles",
                textBody = buildText,
                csvFileName = "lab_trip_miles.csv",
                csvBody = {
                    buildCsvShare(context, 
                        data = data,
                        milesByType = milesByType,
                        listSegs = listSegs,
                    )
                },
                pdfBody = { ReportsLabPdf.fromPlainText("Trip miles", buildText()) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includePersonalInTotals,
                    onCheckedChange = { includePersonalInTotals = it },
                )
                Text(stringResource(R.string.reports_include_personal_in_mile_totals_incl_implicit_le),
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
                Text(stringResource(R.string.reports_show_personal_segments_in_list),
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
                Text(stringResource(R.string.reports_include_zero_length_segments),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(stringResource(R.string.reports_kpis), style = MaterialTheme.typography.titleMedium)
        Text(
            "Total miles: ${UnitFormat.distanceDeltaLabel(totalMiles, context)}" +
                if (!includePersonalInTotals) " (Personal excluded)" else "",
            softWrap = true,
            maxLines = 3,
        )
        Text(
            "Closed segments in list: ${listSegs.count { !it.isOpen }} · Open: $openCount" +
                if (leadingCount > 0) " · Implicit leading Personal: $leadingCount" else "",
            style = MaterialTheme.typography.bodySmall,
            softWrap = true,
        )
        if (isEach) {
            if (milesByVehicleAndType.isEmpty() || eachTypeLabels.isEmpty()) {
                ReportsLabEmpty("No closed trip miles for these filters/toggles.")
            } else {
                milesByVehicleAndType.forEach { (vName, mbt) ->
                    Text(vName, style = MaterialTheme.typography.titleSmall, softWrap = true)
                    mbt.forEach { (type, miles) ->
                        Text(
                            "  $type: ${UnitFormat.distanceDeltaLabel(miles, context)}",
                            style = MaterialTheme.typography.bodyMedium,
                            softWrap = true,
                        )
                    }
                }
                LabMultiSeriesIndexChart(
                    series = eachChartSeries,
                    xLabels = eachTypeLabels,
                    caption = "Miles by type per vehicle (${UnitFormat.distanceUnitShortLabel(context)})",
                )
            }
        } else if (milesByType.isEmpty()) {
            ReportsLabEmpty("No closed trip miles for these filters/toggles.")
        } else {
            milesByType.forEach { (type, miles) ->
                Text(
                    "$type: ${UnitFormat.distanceDeltaLabel(miles, context)}",
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                )
            }
            LabCategoryBarsChart(
                amounts = chartY,
                categoryLabels = chartLabels,
                caption = "Miles by type (${UnitFormat.distanceUnitShortLabel(context)})",
            )
            if (chartLabels.isNotEmpty()) {
                Text(
                    "Bar order: " +
                        chartLabels.mapIndexed { i, t ->
                            val m = milesByType[t] ?: 0
                            "${i + 1}. $t (${UnitFormat.distanceDeltaLabel(m, context)})"
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    softWrap = true,
                    maxLines = 6,
                )
            }
        }

        Text(stringResource(R.string.reports_trip_starts_segments), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reports_chronological_trip_list_separate_from_fuel_histo),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        if (listSegs.isEmpty()) {
            ReportsLabEmpty("No trip segments match the list filters.")
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
                        "${seg.tripType} (implicit)"
                    } else {
                        seg.tripType
                    }
                    Text(
                        "${data.vehicleName(seg.vehicleId)} · $typeLabel · ${formatLabDateTime(seg.startTimestamp)}" +
                            if (canOpen) " · tap to edit" else "",
                        style = MaterialTheme.typography.titleSmall,
                        softWrap = true,
                        maxLines = 3,
                    )
                    val status = when {
                        seg.isOpen -> "Open"
                        seg.isZeroLength -> "Zero-length"
                        seg.isImplicitLeading -> "Closed · leading gap"
                        else -> "Closed"
                    }
                    val milesLabel = if (seg.isOpen) {
                        "n/a"
                    } else {
                        UnitFormat.distanceDeltaLabel(seg.miles, context)
                    }
                    val odoPart = buildString {
                        append(UnitFormat.odometerReadingLabel(seg.startOdo, context))
                        if (seg.endOdo != null) {
                            append(" → ")
                            append(UnitFormat.odometerReadingLabel(seg.endOdo!!, context))
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

private const val TRIP_MILES_INFO =
    "Miles by trip purpose from open-only segments (start → next start). " +
        "Within a report period, miles from the period baseline odometer to the first " +
        "Start trip count as Personal when you never started a purpose earlier " +
        "(same as starting Personal on day 1). If a vehicle has fills but no trip starts, " +
        "baseline → last odo in period also counts as Personal. " +
        "Each vehicle = miles-by-type series and totals per vehicle. " +
        "Segment list is the trip surface (Fuel History lists fills only). " +
        "Not a tax form — export and use elsewhere. " +
        "Period filters by segment start time. Zero-length closed segments are excluded " +
        "from totals by default."

private fun buildTextShare(
    context: android.content.Context,
    data: LabReportData,
    milesByType: Map<String, Int>,
    totalMiles: Int,
    openCount: Int,
    listSegs: List<TripSegments.Segment>,
    includePersonalInTotals: Boolean,
    showZeroLength: Boolean,
    leadingCount: Int,
): String = buildString {
    appendLine("Vehicle Expenses — Trip miles")
    appendLine("Period: ${periodLabel(data.filter)}")
    appendLine("Vehicle: ${data.filterVehicleLabel()}")
    appendLine(
        "Totals: Personal ${if (includePersonalInTotals) "included" else "excluded"}; " +
            "zero-length ${if (showZeroLength) "included" else "excluded"}",
    )
    if (leadingCount > 0) {
        appendLine("Implicit leading Personal segments in window: $leadingCount")
    }
    appendLine("Total miles: ${UnitFormat.distanceDeltaLabel(totalMiles, context)}")
    appendLine("Open segments (in period by start): $openCount")
    appendLine()
    appendLine("Miles by type:")
    if (milesByType.isEmpty()) {
        appendLine("  (none)")
    } else {
        milesByType.forEach { (t, m) ->
            appendLine("  $t: ${UnitFormat.distanceDeltaLabel(m, context)}")
        }
    }
    appendLine()
    appendLine("Segments:")
    listSegs.forEach { seg ->
        val status = when {
            seg.isOpen -> "Open"
            seg.isZeroLength -> "Zero"
            seg.isImplicitLeading -> "Closed-implicit"
            else -> "Closed"
        }
        val miles = if (seg.isOpen) "n/a" else UnitFormat.distanceDeltaLabel(seg.miles, context)
        val endOdo = seg.endOdo?.let { UnitFormat.odometerReadingLabel(it, context) } ?: "…"
        val type = if (seg.isImplicitLeading) "${seg.tripType}(implicit)" else seg.tripType
        appendLine(
            "  ${formatLabDate(seg.startTimestamp)} ${data.vehicleName(seg.vehicleId)} " +
                "$type $status $miles " +
                "${UnitFormat.odometerReadingLabel(seg.startOdo, context)} → $endOdo",
        )
    }
}

private fun buildCsvShare(
    context: android.content.Context,
    data: LabReportData,
    milesByType: Map<String, Int>,
    listSegs: List<TripSegments.Segment>,
): String {
    val unit = UnitFormat.distanceUnitShortLabel(context)
    val sb = StringBuilder()
    sb.appendLine("section,key,value,unit")
    sb.appendLine("meta,period,${ReportsLabShare.csvEscape(periodLabel(data.filter))},")
    sb.appendLine("meta,vehicle,${ReportsLabShare.csvEscape(data.filterVehicleLabel())},")
    milesByType.forEach { (t, m) ->
        sb.appendLine(
            "total_by_type,${ReportsLabShare.csvEscape(t)},$m,$unit",
        )
    }
    // Share CSV column header (English export schema)
    sb.appendLine("segment,date,vehicle,type,status,miles,start_odo,end_odo")
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
