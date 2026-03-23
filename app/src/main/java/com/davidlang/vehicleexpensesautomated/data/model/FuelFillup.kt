package com.davidlang.vehicleexpensesautomated.data.model

import java.time.Instant

data class FuelFillup(
    val id: Long = 0,
    val vehicleId: Int,
    val odometer: Int,
    val gallons: Double,
    val cost: Double,
    val timestamp: Long = Instant.now().toEpochMilli()
)
