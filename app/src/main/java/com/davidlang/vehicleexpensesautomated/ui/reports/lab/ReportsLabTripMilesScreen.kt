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
import com.davidlang.vehicleexpensesautomated.ui.util.S

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
                    vid to TripSegments.milesByType(
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
    val eachChartSeries = remember(milesByVehicleAndType, eachTypeLabels, data.allVehicles) {
        milesByVehicleAndType.mapKeys { (vid, _) -> data.vehicleName(vid) }.mapValues { (_, mbt) ->
            eachTypeLabels.map { t -> (mbt[t] ?: 0).toFloat() }
        }
    }
    val scopeUnit = data.scopeDistanceUnit()
    val totalMiles = milesByType.values.sum()
    val openCount = remember(inPeriod) { TripSegments.openCount(inPeriod) }
    val chartY = remember(milesByType) { milesByType.values.map { it.toFloat() } }
    val chartLabels = remember(milesByType) { milesByType.keys.toList() }
    val leadingCount = remember(inPeriod) { inPeriod.count { it.isImplicitLeading } }

    ReportsLabScreenScaffold(
        title = S("Trip miles"),
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
                subject = S("Trip miles"),
                textBody = buildText,
                csvFileName = "lab_trip_miles.csv",
                csvBody = {
                    buildCsvShare(
                        data = data,
                        milesByType = milesByType,
                        listSegs = listSegs,
                    )
                },
                pdfBody = { ReportsLabPdf.fromPlainText(S("Trip miles"), buildText()) },
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
                    S("Include Personal in mile totals (incl. implicit leading)"),
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
                    S("Show Personal segments in list"),
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
                    S("Include zero-length segments"),
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(S("KPIs"), style = MaterialTheme.typography.titleMedium)
        Text(
            S("Total miles: %1\$s", UnitFormat.distanceDeltaLabel(totalMiles, scopeUnit)) +
                if (!includePersonalInTotals) S(" (Personal excluded)") else "",
            softWrap = true,
            maxLines = 3,
        )
        Text(
            S("Closed segments in list: %1\$s · Open: %2\$s", listSegs.count { !it.isOpen }, openCount) +
                if (leadingCount > 0) S(" · Implicit leading Personal: %1\$s", leadingCount) else "",
            style = MaterialTheme.typography.bodySmall,
            softWrap = true,
        )
        if (isEach) {
            if (milesByVehicleAndType.isEmpty() || eachTypeLabels.isEmpty()) {
                ReportsLabEmpty(S("No closed trip miles for these filters/toggles."))
            } else {
                milesByVehicleAndType.forEach { (vid, mbt) ->
                    val unit = data.odometerUnit(vid)
                    Text(data.vehicleName(vid), style = MaterialTheme.typography.titleSmall, softWrap = true)
                    mbt.forEach { (type, miles) ->
                        Text(
                            "  $type: ${UnitFormat.distanceDeltaLabel(miles, unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            softWrap = true,
                        )
                    }
                }
                LabMultiSeriesIndexChart(
                    series = eachChartSeries,
                    xLabels = eachTypeLabels,
                    caption = S("Miles by type per vehicle (%1\$s)", UnitFormat.distanceUnitShortLabel(scopeUnit)),
                )
            }
        } else if (milesByType.isEmpty()) {
            ReportsLabEmpty(S("No closed trip miles for these filters/toggles."))
        } else {
            milesByType.forEach { (type, miles) ->
                Text(
                    "$type: ${UnitFormat.distanceDeltaLabel(miles, scopeUnit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                )
            }
            LabCategoryBarsChart(
                amounts = chartY,
                categoryLabels = chartLabels,
                caption = S("Miles by type (%1\$s)", UnitFormat.distanceUnitShortLabel(scopeUnit)),
            )
            if (chartLabels.isNotEmpty()) {
                Text(
                    S("Bar order: ") +
                        chartLabels.mapIndexed { i, t ->
                            val m = milesByType[t] ?: 0
                            "${i + 1}. $t (${UnitFormat.distanceDeltaLabel(m, scopeUnit)})"
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    softWrap = true,
                    maxLines = 6,
                )
            }
        }

        Text(S("Trip starts / segments"), style = MaterialTheme.typography.titleMedium)
        Text(
            S("Chronological trip list (separate from Fuel History fills). Tap a row to edit the start fill when it has an id."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        if (listSegs.isEmpty()) {
            ReportsLabEmpty(S("No trip segments match the list filters."))
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
                        S("%1\$s (implicit)", seg.tripType)
                    } else {
                        seg.tripType
                    }
                    Text(
                        "${data.vehicleName(seg.vehicleId)} · $typeLabel · ${formatLabDateTime(seg.startTimestamp)}" +
                            if (canOpen) S(" · tap to edit") else "",
                        style = MaterialTheme.typography.titleSmall,
                        softWrap = true,
                        maxLines = 3,
                    )
                    val status = when {
                        seg.isOpen -> S("Open")
                        seg.isZeroLength -> S("Zero-length")
                        seg.isImplicitLeading -> S("Closed · leading gap")
                        else -> S("Closed")
                    }
                    val milesLabel = if (seg.isOpen) {
                        "n/a"
                    } else {
                        UnitFormat.distanceDeltaLabel(seg.miles, data.odometerUnit(seg.vehicleId))
                    }
                    val odoPart = buildString {
                        val unit = data.odometerUnit(seg.vehicleId)
                        append(UnitFormat.odometerReadingLabel(seg.startOdo, unit))
                        if (seg.endOdo != null) {
                            append(" → ")
                            append(UnitFormat.odometerReadingLabel(seg.endOdo!!, unit))
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
        S("Within a report period, miles from the period baseline odometer to the first ") +
        S("Start trip count as Personal when you never started a purpose earlier ") +
        S("(same as starting Personal on day 1). If a vehicle has fills but no trip starts, ") +
        S("baseline → last odo in period also counts as Personal. ") +
        S("Each vehicle = miles-by-type series and totals per vehicle. ") +
        S("Segment list is the trip surface (Fuel History lists fills only). ") +
        S("Not a tax form — export and use elsewhere. ") +
        S("Period filters by segment start time. Zero-length closed segments are excluded ") +
        S("from totals by default.")

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
    appendLine(S("Vehicle Expenses — Trip miles"))
    appendLine(S("Period: %1\$s", periodLabel(data.filter)))
    appendLine(S("Vehicle: %1\$s", data.filterVehicleLabel()))
    appendLine(
        S("Totals: Personal %1\$s; ", if (includePersonalInTotals) "included" else "excluded") +
            S("zero-length %1\$s", if (showZeroLength) "included" else "excluded"),
    )
    if (leadingCount > 0) {
        appendLine(S("Implicit leading Personal segments in window: %1\$s", leadingCount))
    }
    appendLine(S("Total miles: %1\$s", UnitFormat.distanceDeltaLabel(totalMiles, data.scopeDistanceUnit())))
    appendLine(S("Open segments (in period by start): %1\$s", openCount))
    appendLine()
    appendLine(S("Miles by type:"))
    if (milesByType.isEmpty()) {
        appendLine(S("  (none)"))
    } else {
        milesByType.forEach { (t, m) ->
            appendLine("  $t: ${UnitFormat.distanceDeltaLabel(m, data.scopeDistanceUnit())}")
        }
    }
    appendLine()
    appendLine(S("Segments:"))
    listSegs.forEach { seg ->
        val status = when {
            seg.isOpen -> S("Open")
            seg.isZeroLength -> S("Zero")
            seg.isImplicitLeading -> S("Closed-implicit")
            else -> S("Closed")
        }
        val unit = data.odometerUnit(seg.vehicleId)
        val miles = if (seg.isOpen) "n/a" else UnitFormat.distanceDeltaLabel(seg.miles, unit)
        val endOdo = seg.endOdo?.let { UnitFormat.odometerReadingLabel(it, unit) } ?: "…"
        val type = if (seg.isImplicitLeading) S("%1\$s(implicit)", seg.tripType) else seg.tripType
        appendLine(
            "  ${formatLabDate(seg.startTimestamp)} ${data.vehicleName(seg.vehicleId)} " +
                "$type $status $miles " +
                "${UnitFormat.odometerReadingLabel(seg.startOdo, unit)} → $endOdo",
        )
    }
}

private fun buildCsvShare(
    data: LabReportData,
    milesByType: Map<String, Int>,
    listSegs: List<TripSegments.Segment>,
): String {
    val unit = UnitFormat.distanceUnitShortLabel(data.scopeDistanceUnit())
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
        val segUnit = UnitFormat.distanceUnitShortLabel(data.odometerUnit(seg.vehicleId))
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
                segUnit,
            ).joinToString(","),
        ).append('\n')
    }
    return sb.toString()
}
