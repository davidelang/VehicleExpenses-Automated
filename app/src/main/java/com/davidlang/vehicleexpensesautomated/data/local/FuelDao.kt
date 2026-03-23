package com.davidlang.vehicleexpensesautomated.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Insert
    suspend fun insert(fillup: FuelFillup)

    @Query("SELECT * FROM fuel_fillups WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getFillupsForVehicle(vehicleId: Int): Flow<List<FuelFillup>>

    @Query("SELECT * FROM fuel_fillups ORDER BY timestamp DESC")
    fun getAllFuelFills(): Flow<List<FuelFillup>>
}
