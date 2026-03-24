package com.davidlang.vehicleexpensesautomated.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseEntryDao {
    @Insert
    suspend fun insert(entry: ExpenseEntry)

    @Query("SELECT * FROM expense_entries WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<ExpenseEntry>>

    @Query("SELECT * FROM expense_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<ExpenseEntry>>
}
