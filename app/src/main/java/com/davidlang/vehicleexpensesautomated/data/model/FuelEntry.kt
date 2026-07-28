package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    val odometer: Int,
    /**
     * Volume in the user's **preferred** unit (G or L from settings), not always US gallons.
     * Quick Fill converts UI unit → preferred at save; reports display this value with the preferred label.
     */
    val gallons: Double,
    val cost: Double,
    /** ISO 4217 code preferred; blank = legacy / use settings default at display. */
    val currency: String = "",
    val timestamp: Long,
    val photoUrl: String? = null,
    /**
     * **Explicit full-fill override only** (default false).
     *
     * - **false:** no override. Full-fill anchors use field presence only
     *   (`odo>0 && cost>0 && gallons>0` and not [economyIgnored]).
     * - **true:** odo, cost, **and** volume are all present **and** the user
     *   (checkbox) says: do **not** treat this row as a full-fill chain anchor.
     *
     * Incomplete rows (missing any field) are **not** full fills without setting
     * this flag. Batch/merge/sanitizer must **never** auto-set true for missing data.
     */
    val isPartialFill: Boolean = false,
    /**
     * When true, this row is excluded from economy metrics (MPG legs, avg, $/mi anchors
     * and window cost/vol). Inventory (fuel $, volume, fill counts) still includes it.
     * **Must sync** with the fuel row (not pending-only).
     */
    val economyIgnored: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val cloudManifest: String? = null,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    /** Stable cross-device merge key (UUID string). */
    val syncId: String = "",
    /** Device that created this row; blank until first save after migration. */
    val originDeviceId: String = "",
    /** Last-write-wins timestamp (ms). */
    val updatedAt: Long = 0,
)