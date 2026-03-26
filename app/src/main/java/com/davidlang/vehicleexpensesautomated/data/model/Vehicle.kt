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
    val year: Int,
    val licensePlate: String,
    val vin: String? = null,
    val notes: String? = null,
    val referenceDashPhotoUrl: String? = null
)
