package com.davidlang.vehicleexpensesautomated.data.repository

import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {

    fun getAllVehicles(): Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun getVehicleById(id: Int): Vehicle? = vehicleDao.getVehicleById(id).first()

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        vehicleDao.insertVehicle(vehicle)
        return 0L // Room auto-generates ID; legacy callers expect Long
    }

    // Legacy method for existing CsvManager.kt + SyncWorker.kt
    suspend fun insert(vehicle: Vehicle): Long = insertVehicle(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.updateVehicle(vehicle)

    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.deleteVehicle(vehicle.id)
}
