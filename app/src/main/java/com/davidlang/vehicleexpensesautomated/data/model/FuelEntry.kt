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
    val timestamp: Long,
    val photoUrl: String? = null,
    val isPartialFill: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val cloudManifest: String? = null
)
