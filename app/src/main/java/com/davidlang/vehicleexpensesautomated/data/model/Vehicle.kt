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
    val otherTextCropLeft: Float? = null,     // renamed from landmarkCropLeft
    val otherTextCropTop: Float? = null,
    val otherTextCropRight: Float? = null,
    val otherTextCropBottom: Float? = null,
    val referenceTextBlocks: String? = null,   // existing (raw)
    val landmarkTextBlocksJson: String? = null // new (cleaned/filtered)
)
