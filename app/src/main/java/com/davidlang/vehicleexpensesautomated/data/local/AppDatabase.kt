package com.davidlang.vehicleexpensesautomated.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.davidlang.vehicleexpensesautomated.data.model.FuelFillup

@Database(entities = [FuelFillup::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fuelDao(): FuelDao
}
