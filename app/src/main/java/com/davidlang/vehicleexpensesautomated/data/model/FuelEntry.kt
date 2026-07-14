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
    val isPartialFill: Boolean = false,
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