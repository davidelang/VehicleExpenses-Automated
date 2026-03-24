package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VehicleRepository @Inject constructor(private val dao: VehicleDao) {
    suspend fun insert(vehicle: Vehicle) = dao.insertVehicle(vehicle)
    suspend fun update(vehicle: Vehicle) = dao.updateVehicle(vehicle)
    suspend fun delete(vehicle: Vehicle) = dao.deleteVehicle(vehicle.id)
    
    fun getVehicleById(id: Int): Flow<Vehicle?> = dao.getVehicleById(id)
    fun getAllVehicles(): Flow<List<Vehicle>> = dao.getAllVehicles()
}
