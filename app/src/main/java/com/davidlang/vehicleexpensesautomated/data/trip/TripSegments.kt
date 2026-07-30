package com.davidlang.vehicleexpensesautomated.data.trip

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * Open-only trip **segments** derived from ordered trip starts.
 * Closed segment i: starts[i] → starts[i+1]; open segment = last start with no end.
 * Pure domain: raw ints; UI formats via [com.davidlang.vehicleexpensesautomated.ui.util.UnitFormat].
 */
object TripSegments {

    data class Segment(
        val vehicleId: Int,
        val tripType: String,
        val start: FuelEntry,
        /** Null when this is the open (last) start on the vehicle. */
        val end: FuelEntry?,
        /** endOdo - startOdo when closed and endOdo > startOdo; else 0. */
        val miles: Int,
        val isOpen: Boolean,
        val isZeroLength: Boolean,
        val isPersonal: Boolean,
    ) {
        val startTimestamp: Long get() = start.timestamp
        val startOdo: Int get() = start.odometer
        val endOdo: Int? get() = end?.odometer
        val endTimestamp: Long? get() = end?.timestamp
    }

    /**
     * All segments for [vehicleId] from [entries] (any fuel list; filters trip starts internally).
     * Ordered oldest start first.
     */
    fun listSegments(vehicleId: Int, entries: List<FuelEntry>): List<Segment> {
        val starts = TripTimeline.tripStartsForVehicle(vehicleId, entries)
        if (starts.isEmpty()) return emptyList()
        val out = ArrayList<Segment>(starts.size)
        for (i in starts.indices) {
            val start = starts[i]
            val end = starts.getOrNull(i + 1)
            val isOpen = end == null
            val miles = if (end != null && end.odometer > start.odometer) {
                end.odometer - start.odometer
            } else {
                0
            }
            val type = start.tripType.trim()
            out.add(
                Segment(
                    vehicleId = vehicleId,
                    tripType = type,
                    start = start,
                    end = end,
                    miles = miles,
                    isOpen = isOpen,
                    isZeroLength = !isOpen && miles == 0,
                    isPersonal = type.equals(TripTypes.PERSONAL, ignoreCase = true),
                ),
            )
        }
        return out
    }

    /** Segments for every vehicle that has trip starts in [entries]. */
    fun listAllSegments(entries: List<FuelEntry>): List<Segment> {
        val vehicleIds = entries
            .asSequence()
            .filter { TripTimeline.isTripStart(it) }
            .map { it.vehicleId }
            .distinct()
            .sorted()
            .toList()
        return vehicleIds.flatMap { listSegments(it, entries) }
    }

    /**
     * Filter segments for Lab: period by **start** timestamp; optional personal + zero-length.
     * @param includePersonal when false, personal types still appear if [forList] but totals use [milesForTotals]
     */
    fun filterForPeriod(
        segments: List<Segment>,
        startMsInclusive: Long?,
        endMsInclusive: Long?,
    ): List<Segment> =
        segments.filter { seg ->
            val t = seg.startTimestamp
            (startMsInclusive == null || t >= startMsInclusive) &&
                (endMsInclusive == null || t <= endMsInclusive)
        }

    fun forList(
        segments: List<Segment>,
        includePersonal: Boolean,
        showZeroLength: Boolean,
    ): List<Segment> =
        segments.filter { seg ->
            (includePersonal || !seg.isPersonal) &&
                (showZeroLength || !seg.isZeroLength)
        }

    /**
     * Miles by trip type for **tax totals**: closed segments only; exclude open, zero-length,
     * and (by default) personal.
     */
    fun milesByType(
        segments: List<Segment>,
        includePersonal: Boolean = false,
        includeZeroLength: Boolean = false,
    ): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        for (seg in segments) {
            if (seg.isOpen) continue
            if (!includeZeroLength && seg.isZeroLength) continue
            if (!includePersonal && seg.isPersonal) continue
            map[seg.tripType] = (map[seg.tripType] ?: 0) + seg.miles
        }
        return map
    }

    fun totalMiles(
        segments: List<Segment>,
        includePersonal: Boolean = false,
        includeZeroLength: Boolean = false,
    ): Int = milesByType(segments, includePersonal, includeZeroLength).values.sum()

    fun openCount(segments: List<Segment>): Int = segments.count { it.isOpen }
}
