package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val make: String,
    val model: String,
    val year: Int,
    val referenceDashPhotoUrl: String?,
    val cleanedReferenceDashPhotoUrl: String? = null,  // NEW: pre-processed (ticks removed)
    val odometerCropLeft: Float? = null,
    val odometerCropTop: Float? = null,
    val odometerCropRight: Float? = null,
    val odometerCropBottom: Float? = null,
    val landmarkCropLeft: Float? = null,
    val landmarkCropTop: Float? = null,
    val landmarkCropRight: Float? = null,
    val landmarkCropBottom: Float? = null
)
