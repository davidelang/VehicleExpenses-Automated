package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY make, model")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY make, model")
    suspend fun getAllIncludingDeleted(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE originDeviceId = :originDeviceId AND id = :id LIMIT 1")
    suspend fun findBySyncKey(originDeviceId: String, id: Int): Vehicle?

    @Query("SELECT * FROM vehicles WHERE syncId = :syncId LIMIT 1")
    suspend fun findBySyncId(syncId: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun getVehicleById(id: Int): Flow<Vehicle?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: Int)
}
