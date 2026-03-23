package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.local.FuelDao
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FuelRepository @Inject constructor(private val dao: FuelDao) {

    suspend fun saveFillup(fillup: FuelFillup) {
        dao.insert(fillup)
    }

    fun getFillupsForVehicle(vehicleId: Int): Flow<List<FuelFillup>> = dao.getFillupsForVehicle(vehicleId)

    fun getAllFuelFills(): Flow<List<FuelFillup>> = dao.getAllFuelFills()
}
