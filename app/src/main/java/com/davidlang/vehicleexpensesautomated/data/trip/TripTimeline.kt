package com.davidlang.vehicleexpensesautomated.data.trip

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

/**
 * Open-only trip model: a fuel row with non-blank [FuelEntry.tripType] is a trip **start**
 * at that odometer. There is no close row — the next start on the same vehicle ends the
 * prior segment (open→open). Segment identity = fuel row [FuelEntry.syncId].
 */
object TripTimeline {

    fun isTripStart(entry: FuelEntry): Boolean =
        !entry.deleted && entry.tripType.isNotBlank()

    /** Non-deleted trip starts for [vehicleId], ordered by (timestamp, id). */
    fun tripStartsForVehicle(vehicleId: Int, entries: List<FuelEntry>): List<FuelEntry> =
        entries
            .asSequence()
            .filter { it.vehicleId == vehicleId && isTripStart(it) }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
            .toList()

    /**
     * Active open trip for the vehicle = last trip start by (timestamp, id).
     * Null when no trip starts exist yet (implicit personal until first open).
     */
    fun currentOpenTrip(vehicleId: Int, entries: List<FuelEntry>): FuelEntry? =
        tripStartsForVehicle(vehicleId, entries).lastOrNull()

    fun currentOpenTripType(vehicleId: Int, entries: List<FuelEntry>): String? =
        currentOpenTrip(vehicleId, entries)?.tripType?.takeIf { it.isNotBlank() }

    /**
     * Build a trip-start fuel row: gallons=0, cost=0, [tripType] set.
     * Caller supplies [syncId]/[originDeviceId]/[updatedAt] stamps or leaves defaults
     * for repository stamp on insert.
     */
    fun buildTripStart(
        vehicleId: Int,
        odometer: Int,
        tripType: String,
        timestamp: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        photoUrl: String? = null,
        syncId: String = "",
        originDeviceId: String = "",
        updatedAt: Long = 0,
    ): FuelEntry {
        val type = tripType.trim()
        require(type.isNotEmpty()) { "tripType must be non-blank for a trip start" }
        require(odometer > 0) { "odometer required for trip start" }
        return FuelEntry(
            vehicleId = vehicleId,
            odometer = odometer,
            gallons = 0.0,
            cost = 0.0,
            currency = "",
            timestamp = timestamp,
            photoUrl = photoUrl,
            isPartialFill = false,
            economyIgnored = false,
            latitude = latitude,
            longitude = longitude,
            location = null,
            notes = null,
            tripType = type,
            syncId = syncId,
            originDeviceId = originDeviceId,
            updatedAt = updatedAt,
        )
    }
}
