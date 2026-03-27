package com.davidlang.vehicleexpensesautomated.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry

@Database(
    entities = [Vehicle::class, FuelEntry::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN referenceDashPhotoUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN odometerCropLeft REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN odometerCropTop REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN odometerCropRight REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN odometerCropBottom REAL NOT NULL DEFAULT 1.0")
            }
        }
    }
}
