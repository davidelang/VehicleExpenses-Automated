package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelFillDao {
    @Query("SELECT * FROM fuel_fills WHERE vehicleId = :vehicleId ORDER BY dateMillis DESC")
    fun getFuelFillsForVehicle(vehicleId: Int): Flow<List<FuelFill>>

    @Insert
    suspend fun insertFuelFill(fuelFill: FuelFill)
}
