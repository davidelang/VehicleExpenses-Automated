package com.davidlang.vehicleexpensesautomated.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry   // assuming you have this from earlier

@Database(
    entities = [Vehicle::class, FuelEntry::class /* add any other entities you already have */],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    // abstract fun fuelDao(): FuelDao   // add if you have one

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN referenceDashPhotoUrl TEXT")
            }
        }
    }
}
