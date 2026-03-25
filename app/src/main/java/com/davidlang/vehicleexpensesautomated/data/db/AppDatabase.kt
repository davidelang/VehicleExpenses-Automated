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
                // idempotent helper
                fun columnExists(table: String, column: String): Boolean {
                    val cursor = db.query("PRAGMA table_info($table)")
                    return cursor.use { c ->
                        while (c.moveToNext()) {
                            if (c.getString(1) == column) return true
                        }
                        false
                    }
                }

                // fuel_entries (isPartialFill – the recent change)
                if (!columnExists("fuel_entries", "isPartialFill")) {
                    db.execSQL("ALTER TABLE fuel_entries ADD COLUMN isPartialFill INTEGER NOT NULL DEFAULT 0")
                }

                // expense_entries – these two columns were added to the entity but never migrated
                if (!columnExists("expense_entries", "category")) {
                    db.execSQL("ALTER TABLE expense_entries ADD COLUMN category TEXT NOT NULL DEFAULT 'Other'")
                }
                if (!columnExists("expense_entries", "receiptImagePath")) {
                    db.execSQL("ALTER TABLE expense_entries ADD COLUMN receiptImagePath TEXT")
                }
            }
        }
    }
}
