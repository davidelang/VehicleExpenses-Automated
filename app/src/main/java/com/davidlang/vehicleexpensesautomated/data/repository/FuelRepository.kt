package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Stub repository (replace with real Room DAO later)
class FuelRepository {
    private val fillups = mutableListOf<FuelFillup>()

    suspend fun saveFillup(fillup: FuelFillup) {
        fillups.add(fillup.copy(id = System.currentTimeMillis()))
        println("💾 SAVED: $fillup")
    }

    fun getFillupsForVehicle(vehicleId: Int): Flow<List<FuelFillup>> = flow {
        emit(fillups.filter { it.vehicleId == vehicleId })
    }
}
