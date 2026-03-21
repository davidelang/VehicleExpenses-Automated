package com.davidlang.vehicleexpensesautomated.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelFillDao
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.model.Expense
import com.davidlang.vehicleexpensesautomated.data.model.FuelFill

@Database(
    entities = [Vehicle::class, Expense::class, FuelFill::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun fuelFillDao(): FuelFillDao
}
