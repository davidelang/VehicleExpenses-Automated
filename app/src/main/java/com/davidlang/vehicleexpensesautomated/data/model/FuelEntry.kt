package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Int,
    val odometer: Int,
    val gallons: Double,
    val cost: Double,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val photoUrl: String? = null   // ← NEW: stores Drive public URL
)
