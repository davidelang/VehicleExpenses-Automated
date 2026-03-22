package com.davidlang.vehicleexpensesautomated.repository

import com.davidlang.vehicleexpensesautomated.data.dao.FuelFillDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FuelRepository @Inject constructor(
    private val dao: FuelFillDao
) {
    fun getFuelFillsForVehicle(vehicleId: Int): Flow<List<FuelFill>> = dao.getFuelFillsForVehicle(vehicleId)

    fun getAllFuelFills(): Flow<List<FuelFill>> = dao.getAllFuelFills()

    suspend fun insert(fuelFill: FuelFill) = dao.insertFuelFill(fuelFill)

    suspend fun delete(fuelFill: FuelFill) = dao.deleteFuelFill(fuelFill)
}
