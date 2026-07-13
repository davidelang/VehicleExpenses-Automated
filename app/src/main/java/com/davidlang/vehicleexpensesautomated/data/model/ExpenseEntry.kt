package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_entries")
data class ExpenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    /** Ordered vehicle syncId list (JSON array string); primary/first vehicle first. */
    val vehicleSyncIdsJson: String = "",
    val amount: Double,
    /** ISO 4217 code preferred; blank = legacy / use settings default at display. */
    val currency: String = "",
    val description: String,
    val date: Long,
    val photoUrl: String? = null,
    val category: String = "Other",
    /** @deprecated Legacy local image path; use [photoUrl]. Column retained for migration safety. */
    @Deprecated("Use photoUrl; no longer written by app code")
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val cloudManifest: String? = null,
    /** Free-text vendor / payee (separate from description notes). */
    val vendor: String = "",
    /** Optional odometer at expense time. */
    val odometer: Int? = null,
    /** Stable cross-device merge key (UUID string). */
    val syncId: String = "",
    /** Device that created this row; blank until first save after migration. */
    val originDeviceId: String = "",
    /** Last-write-wins timestamp (ms). */
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val deletedAt: Long? = null
)
