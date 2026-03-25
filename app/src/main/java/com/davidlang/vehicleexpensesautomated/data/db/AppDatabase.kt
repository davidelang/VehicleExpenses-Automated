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
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun expenseEntryDao(): ExpenseEntryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Make migration idempotent so it never fails with "duplicate column"
                // on devices where the column was already present but version was still 2
                val cursor = db.query("PRAGMA table_info(fuel_entries)")
                val columnExists = cursor.use { c ->
                    var exists = false
                    while (c.moveToNext()) {
                        if (c.getString(1) == "isPartialFill") {
                            exists = true
                            break
                        }
                    }
                    exists
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE fuel_entries ADD COLUMN isPartialFill INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
    }
}
