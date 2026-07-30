package com.davidlang.vehicleexpensesautomated.ui.reports.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun ReportsLabTripMilesScreen(navController: NavHostController) {
    val data = rememberLabReportData()
    var includePersonalInTotals by remember { mutableStateOf(false) }
    var showZeroLength by remember { mutableStateOf(false) }
    // Tax totals default exclude Personal; list may still show Personal segments.
    var showPersonalInList by remember { mutableStateOf(true) }

    val (periodStart, periodEnd) = remember(data.filter) { periodBounds(data.filter) }

    val baseSegments = remember(data.allFuel, data.filter.vehicleId) {
        val fuel = data.allFuel
        when (val vid = data.filter.vehicleId) {
            null -> TripSegments.listAllSegments(fuel)
            else -> TripSegments.listSegments(vid, fuel)
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
    val milesByType = remember(inPeriod, includePersonalInTotals, showZeroLength) {
        TripSegments.milesByType(
            inPeriod,
            includePersonal = includePersonalInTotals,
            includeZeroLength = showZeroLength,
        )
    }
    val totalMiles = milesByType.values.sum()
    val openCount = remember(inPeriod) { TripSegments.openCount(inPeriod) }
    val chartY = remember(milesByType) { milesByType.values.map { it.toFloat() } }
    val chartLabels = remember(milesByType) { milesByType.keys.toList() }

    ReportsLabScreenScaffold(
        title = "Trip miles",
        subtitle = "Miles by trip type from open-only segments (start→next start). " +
            "Period uses segment **start** time. Implicit personal before first start is out of scope.",
        filterState = data.filter,
        vehicles = data.vehicles,
        onFilterChange = data.setFilter,
        shareRow = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val body = buildTextShare(
                                data = data,
                                milesByType = milesByType,
                                totalMiles = totalMiles,
                                openCount = openCount,
                                listSegs = listSegs,
                                includePersonalInTotals = includePersonalInTotals,
                                showZeroLength = showZeroLength,
                            )
                            ReportsLabShare.shareText(data.context, "Trip miles", body)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Share TEXT", maxLines = 2, softWrap = true)
                    }
                    OutlinedButton(
                        onClick = {
                            val csv = buildCsvShare(
                                data = data,
                                milesByType = milesByType,
                                listSegs = listSegs,
                            )
                            ReportsLabShare.shareCsv(
                                data.context,
                                "lab_trip_miles.csv",
                                csv,
                                "Trip miles CSV",
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Share CSV", maxLines = 2, softWrap = true)
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includePersonalInTotals,
                    onCheckedChange = { includePersonalInTotals = it },
                )
                Text(
                    "Include Personal in mile totals",
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
                    "Show Personal segments in list",
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
                    "Include zero-length segments",
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text("KPIs", style = MaterialTheme.typography.titleMedium)
        Text(
            "Total miles: ${UnitFormat.distanceDeltaLabel(totalMiles)}" +
                if (!includePersonalInTotals) " (Personal excluded)" else "",
            softWrap = true,
            maxLines = 3,
        )
        Text(
            "Closed segments in list: ${listSegs.count { !it.isOpen }} · Open: $openCount",
            style = MaterialTheme.typography.bodySmall,
            softWrap = true,
        )
        if (milesByType.isEmpty()) {
            ReportsLabEmpty("No closed trip miles for these filters/toggles.")
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
                caption = "Miles by type (${UnitFormat.distanceUnitShortLabel()})",
            )
            if (chartLabels.isNotEmpty()) {
                Text(
                    "Bar order: " +
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

        Text("Segments", style = MaterialTheme.typography.titleMedium)
        if (listSegs.isEmpty()) {
            ReportsLabEmpty("No trip segments match the list filters.")
        } else {
            listSegs.forEach { seg ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "${data.vehicleName(seg.vehicleId)} · ${seg.tripType} · ${formatLabDateTime(seg.startTimestamp)}",
                        style = MaterialTheme.typography.titleSmall,
                        softWrap = true,
                        maxLines = 3,
                    )
                    val status = when {
                        seg.isOpen -> "Open"
                        seg.isZeroLength -> "Zero-length"
                        else -> "Closed"
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

private fun buildTextShare(
    data: LabReportData,
    milesByType: Map<String, Int>,
    totalMiles: Int,
    openCount: Int,
    listSegs: List<TripSegments.Segment>,
    includePersonalInTotals: Boolean,
    showZeroLength: Boolean,
): String = buildString {
    appendLine("Vehicle Expenses — Trip miles (experimental)")
    appendLine("Period: ${periodLabel(data.filter)}")
    appendLine("Vehicle: ${data.filterVehicleLabel()}")
    appendLine(
        "Totals: Personal ${if (includePersonalInTotals) "included" else "excluded"}; " +
            "zero-length ${if (showZeroLength) "included" else "excluded"}",
    )
    appendLine("Total miles: ${UnitFormat.distanceDeltaLabel(totalMiles)}")
    appendLine("Open segments (in period by start): $openCount")
    appendLine()
    appendLine("Miles by type:")
    if (milesByType.isEmpty()) {
        appendLine("  (none)")
    } else {
        milesByType.forEach { (t, m) ->
            appendLine("  $t: ${UnitFormat.distanceDeltaLabel(m)}")
        }
    }
    appendLine()
    appendLine("Segments:")
    listSegs.forEach { seg ->
        val status = when {
            seg.isOpen -> "Open"
            seg.isZeroLength -> "Zero"
            else -> "Closed"
        }
        val miles = if (seg.isOpen) "n/a" else UnitFormat.distanceDeltaLabel(seg.miles)
        val endOdo = seg.endOdo?.let { UnitFormat.odometerReadingLabel(it) } ?: "…"
        appendLine(
            "  ${formatLabDate(seg.startTimestamp)} ${data.vehicleName(seg.vehicleId)} " +
                "${seg.tripType} $status $miles " +
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
        "segment_header,date,vehicle,type,status,miles,start_odo,end_odo,unit",
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
                unit,
            ).joinToString(","),
        ).append('\n')
    }
    return sb.toString()
}
