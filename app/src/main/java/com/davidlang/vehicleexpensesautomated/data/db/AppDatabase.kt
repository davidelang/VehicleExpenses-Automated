package com.davidlang.vehicleexpensesautomated.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.model.ExpenseEntry
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

@Database(
    entities = [Vehicle::class, FuelEntry::class, ExpenseEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun expenseEntryDao(): ExpenseEntryDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fuel_entries ADD COLUMN isMissedFillup INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
