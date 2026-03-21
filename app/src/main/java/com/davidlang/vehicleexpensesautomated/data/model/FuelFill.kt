package com.davidlang.vehicleexpensesautomated.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_fills",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class,
        parentColumns = ["id"],
        childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FuelFill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val gallons: Double,
    val pricePerGallon: Double,
    val totalCost: Double,
    val odometer: Int,
    val dateMillis: Long,          // ← changed to Long (epoch millis)
    val fuelType: String? = null,
    val notes: String? = null
)
