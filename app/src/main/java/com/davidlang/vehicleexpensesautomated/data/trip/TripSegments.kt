package com.davidlang.vehicleexpensesautomated.data.trip

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * Open-only trip **segments** derived from ordered trip starts.
 * Closed segment i: starts[i] → starts[i+1]; open segment = last start with no end.
 *
 * **Implicit personal (report period):** When a vehicle has no trip start before
 * [periodStartMs], miles from the period baseline odo (latest odo at or before period
 * start; for all-time: earliest odo) to the first start in/after the period count as
 * [TripTypes.PERSONAL] — same as starting Personal on day 1 of the window.
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
        /**
         * True when this row is the synthetic leading Personal gap for a report period
         * (no real Start trip before first purpose in window).
         */
        val isImplicitLeading: Boolean = false,
    ) {
        val startTimestamp: Long get() = start.timestamp
        val startOdo: Int get() = start.odometer
        val endOdo: Int? get() = end?.odometer
        val endTimestamp: Long? get() = end?.timestamp
    }

    /**
     * All natural segments for [vehicleId] from [entries] (any fuel list; filters trip starts).
     * Ordered oldest start first. Does **not** add implicit personal.
     */
    fun listSegments(vehicleId: Int, entries: List<FuelEntry>): List<Segment> {
        val starts = TripTimeline.tripStartsForVehicle(vehicleId, entries)
        if (starts.isEmpty()) return emptyList()
        val out = ArrayList<Segment>(starts.size)
        for (i in starts.indices) {
            val start = starts[i]
            val end = starts.getOrNull(i + 1)
            out.add(segmentBetween(vehicleId, start, end, isImplicitLeading = false))
        }
        return out
    }

    /** Segments for every vehicle that has trip starts in [entries] (natural only). */
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
     * Natural segments plus **implicit leading Personal** for the report window when
     * there is no trip start before [periodStartMs] (null start = all-time / epoch 0).
     *
     * Baseline odo: latest odo-bearing row for the vehicle at or before period start;
     * for all-time (null start), earliest odo-bearing row. If none, no leading miles.
     * Leading ends at the first trip start with timestamp ≥ period start.
     */
    fun listSegmentsWithImplicitPersonal(
        vehicleId: Int,
        entries: List<FuelEntry>,
        periodStartMs: Long?,
        periodEndMs: Long? = null,
    ): List<Segment> {
        val natural = listSegments(vehicleId, entries)
        val leading = leadingImplicitPersonal(
            vehicleId = vehicleId,
            entries = entries,
            periodStartMs = periodStartMs,
            periodEndMs = periodEndMs,
        ) ?: return natural
        // Keep list ordered by start time (leading first when present)
        return (listOf(leading) + natural).sortedWith(
            compareBy({ it.startTimestamp }, { it.start.id }),
        )
    }

    fun listAllSegmentsWithImplicitPersonal(
        entries: List<FuelEntry>,
        periodStartMs: Long?,
        periodEndMs: Long? = null,
    ): List<Segment> {
        val vehicleIds = entries
            .asSequence()
            .filter { !it.deleted && it.vehicleId > 0 }
            .map { it.vehicleId }
            .distinct()
            .sorted()
            .toList()
        // Include vehicles that only have fills (no starts) so leading / TR2 personal can form.
        val withStarts = entries
            .asSequence()
            .filter { TripTimeline.isTripStart(it) }
            .map { it.vehicleId }
            .toSet()
        val ids = (vehicleIds + withStarts).distinct().sorted()
        return ids.flatMap {
            listSegmentsWithImplicitPersonal(it, entries, periodStartMs, periodEndMs)
        }
    }

    /**
     * Leading Personal closed segment, or null when not applicable.
     * @see listSegmentsWithImplicitPersonal
     */
    fun leadingImplicitPersonal(
        vehicleId: Int,
        entries: List<FuelEntry>,
        periodStartMs: Long?,
        periodEndMs: Long? = null,
    ): Segment? {
        val windowStart = periodStartMs ?: 0L
        val starts = TripTimeline.tripStartsForVehicle(vehicleId, entries)
        // Any start before the window means purpose already open — not implicit personal
        if (starts.any { it.timestamp < windowStart }) return null
        val firstInOrAfter = starts.firstOrNull { it.timestamp >= windowStart }
        val baselineOdo = periodBaselineOdo(vehicleId, entries, periodStartMs) ?: return null
        if (firstInOrAfter != null) {
            if (firstInOrAfter.odometer <= baselineOdo) return null
            val miles = firstInOrAfter.odometer - baselineOdo
            val pseudoStart = firstInOrAfter.copy(
                id = 0L,
                odometer = baselineOdo,
                tripType = TripTypes.PERSONAL,
                timestamp = windowStart,
                gallons = 0.0,
                cost = 0.0,
                notes = "implicit_personal_leading",
                photoUrl = null,
            )
            return segmentBetween(
                vehicleId = vehicleId,
                start = pseudoStart,
                end = firstInOrAfter,
                isImplicitLeading = true,
            ).copy(miles = miles, isZeroLength = miles == 0)
        }
        // TR2: no trip starts in history (or none in/after window after "any before" check).
        // Count period baseline → last odo-bearing fill in period as Personal.
        if (starts.isNotEmpty()) return null
        val lastInPeriod = lastOdoInPeriod(vehicleId, entries, periodStartMs, periodEndMs)
            ?: return null
        if (lastInPeriod.odometer <= baselineOdo) return null
        val miles = lastInPeriod.odometer - baselineOdo
        val pseudoStart = lastInPeriod.copy(
            id = 0L,
            odometer = baselineOdo,
            tripType = TripTypes.PERSONAL,
            timestamp = windowStart,
            gallons = 0.0,
            cost = 0.0,
            notes = "implicit_personal_no_starts",
            photoUrl = null,
        )
        return segmentBetween(
            vehicleId = vehicleId,
            start = pseudoStart,
            end = lastInPeriod,
            isImplicitLeading = true,
        ).copy(miles = miles, isZeroLength = miles == 0)
    }

    /** Latest odo-bearing row for vehicle inside [periodStartMs, periodEndMs] (null = open bound). */
    fun lastOdoInPeriod(
        vehicleId: Int,
        entries: List<FuelEntry>,
        periodStartMs: Long?,
        periodEndMs: Long?,
    ): FuelEntry? {
        return entries
            .filter {
                !it.deleted && it.vehicleId == vehicleId && it.odometer > 0 &&
                    (periodStartMs == null || it.timestamp >= periodStartMs) &&
                    (periodEndMs == null || it.timestamp <= periodEndMs)
            }
            .maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
    }

    /**
     * Odo at period boundary: latest odo-bearing non-deleted row for [vehicleId]
     * with timestamp ≤ period start. All-time (null): earliest odo-bearing row.
     */
    fun periodBaselineOdo(
        vehicleId: Int,
        entries: List<FuelEntry>,
        periodStartMs: Long?,
    ): Int? {
        val live = entries.filter {
            !it.deleted && it.vehicleId == vehicleId && it.odometer > 0
        }
        if (live.isEmpty()) return null
        return if (periodStartMs == null) {
            live.minWithOrNull(compareBy({ it.timestamp }, { it.id }))?.odometer
        } else {
            live
                .filter { it.timestamp <= periodStartMs }
                .maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
                ?.odometer
        }
    }

    private fun segmentBetween(
        vehicleId: Int,
        start: FuelEntry,
        end: FuelEntry?,
        isImplicitLeading: Boolean,
    ): Segment {
        val isOpen = end == null
        val miles = if (end != null && end.odometer > start.odometer) {
            end.odometer - start.odometer
        } else {
            0
        }
        val type = start.tripType.trim()
        return Segment(
            vehicleId = vehicleId,
            tripType = type,
            start = start,
            end = end,
            miles = miles,
            isOpen = isOpen,
            isZeroLength = !isOpen && miles == 0,
            isPersonal = type.equals(TripTypes.PERSONAL, ignoreCase = true),
            isImplicitLeading = isImplicitLeading,
        )
    }

    /**
     * Filter segments for Lab: period by **start** timestamp; optional personal + zero-length.
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
     * Miles by trip type for totals: closed segments only; exclude open, zero-length,
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
