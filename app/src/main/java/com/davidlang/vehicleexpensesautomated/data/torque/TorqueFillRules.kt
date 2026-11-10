package com.davidlang.vehicleexpensesautomated.data.torque

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.repository.isSystemUnassignedVehicle

/** What one source write does to the revision high-water. An acknowledgement is not a write. */
object TorqueFillRules {
    const val ACCEPTED = "ACCEPTED"
    const val DUPLICATE = "DUPLICATE"
    const val STALE = "STALE"
    const val CONFLICT = "CONFLICT"
    const val INVALID_EVENT = "INVALID_EVENT"
    const val RETRY_LATER = "RETRY_LATER"

    enum class Kind { NONE, UPSERT, VOID }

    data class Decision(val kind: Kind, val voidReason: String?)

    /** Old frozen bytes used vehicle.odometerUnit and failed plugin validation. */
    fun needsOldWireRecovery(canonical: String): Boolean =
        canonical.contains("\"odometerUnit\"")

    fun eligible(vehicle: Vehicle?): Boolean =
        vehicle != null &&
            !vehicle.deleted &&
            vehicle.integrateWithTorque &&
            !isSystemUnassignedVehicle(vehicle)

    /** Photo path, location, notes, and updatedAt are not a fill change. */
    fun sameFill(a: FuelEntry, b: FuelEntry): Boolean =
        a.vehicleId == b.vehicleId &&
            a.odometer == b.odometer &&
            a.torqueOdometerRawKm == b.torqueOdometerRawKm &&
            a.odometerSource == b.odometerSource &&
            a.gallons == b.gallons &&
            a.cost == b.cost &&
            a.currency == b.currency &&
            a.timestamp == b.timestamp &&
            a.isPartialFill == b.isPartialFill &&
            a.economyIgnored == b.economyIgnored &&
            a.deleted == b.deleted &&
            a.tripType == b.tripType &&
            a.costSource == b.costSource &&
            a.volumeSource == b.volumeSource &&
            a.syncId == b.syncId

    /**
     * Sheet rows do not carry local provenance. Keep it when the amount did not change
     * so a no-op sync does not allocate a revision.
     */
    fun keepSources(existing: FuelEntry, incoming: FuelEntry): FuelEntry {
        var row = incoming
        if (incoming.odometerSource.isBlank() && incoming.odometer == existing.odometer) {
            row = row.copy(
                odometerSource = existing.odometerSource,
                torqueOdometerRawKm = existing.torqueOdometerRawKm,
            )
        }
        if (incoming.costSource.isBlank() && incoming.cost == existing.cost) {
            row = row.copy(costSource = existing.costSource)
        }
        if (incoming.volumeSource.isBlank() && incoming.gallons == existing.gallons) {
            row = row.copy(volumeSource = existing.volumeSource)
        }
        return row
    }

    fun decide(
        changed: Boolean,
        eligible: Boolean,
        highWater: Long,
        deleted: Boolean,
        trip: Boolean,
        hardRemoved: Boolean,
    ): Decision {
        val removing = deleted || trip || hardRemoved
        if (!changed && !removing) return Decision(Kind.NONE, null)
        if (removing) {
            if (highWater <= 0L || !eligible) return Decision(Kind.NONE, null)
            val reason = if (trip && !deleted && !hardRemoved) "trip_marker" else "deleted"
            return Decision(Kind.VOID, reason)
        }
        if (!eligible || !changed) return Decision(Kind.NONE, null)
        return Decision(Kind.UPSERT, null)
    }

    /** Clear, hold, or leave the pending row. A missing row stays missing. */
    fun applyAck(pending: TorqueFillOutboxEntity?, status: String, revision: Long, payloadHash: String): TorqueFillOutboxEntity? {
        if (pending == null) return null
        if (pending.revision != revision || pending.payloadHash != payloadHash) return pending
        return when (status) {
            ACCEPTED, DUPLICATE, STALE -> null
            CONFLICT, INVALID_EVENT -> pending.copy(held = true)
            else -> pending
        }
    }
}
