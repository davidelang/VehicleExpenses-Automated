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
    val cleanedReferenceDashPhotoUrl: String? = null,  // ticks removed for better matching
    val odometerCropLeft: Float? = null,
    val odometerCropTop: Float? = null,
    val odometerCropRight: Float? = null,
    val odometerCropBottom: Float? = null,
    val landmarkCropLeft: Float? = null,
    val landmarkCropTop: Float? = null,
    val landmarkCropRight: Float? = null,
    val landmarkCropBottom: Float? = null
)
