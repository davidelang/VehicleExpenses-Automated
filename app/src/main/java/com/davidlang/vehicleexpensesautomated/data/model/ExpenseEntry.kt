package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_entries")
data class ExpenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    val amount: Double,
    /** ISO 4217 code preferred; blank = legacy / use settings default at display. */
    val currency: String = "",
    val description: String,
    /** Free-text vendor / payee (separate from description notes). */
    val vendor: String = "",
    val category: String = "Other",
    val date: Long,
    /** Optional odometer at expense time. */
    val odometer: Int? = null,
    val photoUrl: String? = null,
    /**
     * Sole geo/place package: JSON blob (lat/lon/accuracyM/name/address/confirmed/…).
     * See [com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson].
     */
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
    /** Ordered vehicle syncId list (JSON array string); primary/first vehicle first. */
    val vehicleSyncIdsJson: String = "",
)