package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val licensePlate: String? = null,
    val vin: String? = null,
    val notes: String? = null,
    val referenceDashPhotoUrl: String? = null,
    val cleanedReferenceDashPhotoUrl: String? = null,
    val odometerCropLeft: Float? = null,
    val odometerCropTop: Float? = null,
    val odometerCropRight: Float? = null,
    val odometerCropBottom: Float? = null,
    val otherTextCropLeft: Float? = null,
    val otherTextCropTop: Float? = null,
    val otherTextCropRight: Float? = null,
    val otherTextCropBottom: Float? = null,
    val landmarkTextBlocksJson: String? = null,
    /**
     * Ordered trip type names as a JSON string array (e.g. `["Business","Personal",…]`).
     * First entry is the Start-trip dropdown default. Blank means seed/inherit at insert time.
     */
    val tripTypesJson: String = "",
    /**
     * Ordered expense category names as a JSON string array.
     * First entry is the new-expense default. Blank means seed/inherit at insert time.
     * See [com.davidlang.vehicleexpensesautomated.data.expense.ExpenseCategories].
     */
    val expenseCategoriesJson: String = "",
    /**
     * Nominal odometer face width in digits (mechanical/digital). Default 6.
     * OCR prefers this length; see [odometerRolloverCount] for wrap encoding.
     */
    val odometerDigitCount: Int = 6,
    /**
     * How many times the face has rolled past 10^[odometerDigitCount].
     * [com.davidlang.vehicleexpensesautomated.data.model.FuelEntry.odometer] stores **tracking**
     * miles: `rolloverCount * 10^digitCount + displayReading` (no permanent extra face digits).
     */
    val odometerRolloverCount: Int = 0,
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