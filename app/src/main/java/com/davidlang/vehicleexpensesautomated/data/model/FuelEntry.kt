package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    val odometer: Int,
    val gallons: Double,
    val cost: Double,
    val timestamp: Long,
    val photoUrl: String? = null,
    val isPartialFill: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val cloudManifest: String? = null
)
