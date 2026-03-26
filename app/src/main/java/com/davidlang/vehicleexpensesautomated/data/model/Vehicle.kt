package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,                  // new required name field
    val make: String? = null,          // now optional
    val model: String? = null,         // now optional
    val year: Int,
    val licensePlate: String,
    val vin: String? = null,
    val notes: String? = null,
    val referenceDashPhotoUrl: String? = null
)
