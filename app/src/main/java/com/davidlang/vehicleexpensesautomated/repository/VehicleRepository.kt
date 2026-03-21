package com.davidlang.vehicleexpensesautomated.repository

import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {
    val allVehicles: Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun insert(vehicle: Vehicle) {
        vehicleDao.insertVehicle(vehicle)
    }

    suspend fun update(vehicle: Vehicle) {
        vehicleDao.updateVehicle(vehicle)
    }

    suspend fun delete(vehicle: Vehicle) {
        vehicleDao.deleteVehicle(vehicle.id)
    }
}
