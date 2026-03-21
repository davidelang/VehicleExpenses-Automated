package com.davidlang.vehicleexpensesautomated.repository

import com.davidlang.vehicleexpensesautomated.data.dao.FuelFillDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FuelRepository @Inject constructor(
    private val fuelFillDao: FuelFillDao
) {
    fun getFuelFillsForVehicle(vehicleId: Int): Flow<List<FuelFill>> = fuelFillDao.getFuelFillsForVehicle(vehicleId)

    suspend fun insert(fuelFill: FuelFill) {
        fuelFillDao.insertFuelFill(fuelFill)
    }
}
