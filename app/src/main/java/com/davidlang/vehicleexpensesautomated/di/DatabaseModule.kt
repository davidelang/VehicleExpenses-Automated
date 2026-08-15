package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.davidlang.vehicleexpensesautomated.data.batch.FuelLocationJson
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.KnownStationDao
import com.davidlang.vehicleexpensesautomated.data.dao.MergeAckDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN cloudManifest TEXT")
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN cloudManifest TEXT")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN isIcrs INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN odometer INTEGER")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN vendor TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN deletedAt INTEGER")
            db.execSQL("UPDATE fuel_entries SET updatedAt = timestamp WHERE updatedAt = 0")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN deletedAt INTEGER")
            db.execSQL("UPDATE expense_entries SET updatedAt = date WHERE updatedAt = 0")
            db.execSQL(
                "UPDATE expense_entries SET photoUrl = receiptImagePath " +
                    "WHERE photoUrl IS NULL AND receiptImagePath IS NOT NULL"
            )
            db.execSQL("ALTER TABLE vehicles ADD COLUMN cloudManifest TEXT")
            db.execSQL("ALTER TABLE vehicles ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE vehicles ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE vehicles ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE vehicles ADD COLUMN deletedAt INTEGER")
            val now = System.currentTimeMillis()
            db.execSQL("UPDATE vehicles SET updatedAt = $now WHERE updatedAt = 0")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expense_entries ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE fuel_entries ADD COLUMN economyIgnored INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS merge_acks (
                    ackId TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    memberSyncIds TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    originDeviceId TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE fuel_entries ADD COLUMN notes TEXT")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE fuel_entries ADD COLUMN tripType TEXT NOT NULL DEFAULT ''",
            )
            db.execSQL(
                "ALTER TABLE vehicles ADD COLUMN tripTypesJson TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    /**
     * v18: fold latitude/longitude columns into location JSON blob; drop coord columns.
     * Accuracy unknown for legacy rows. Place text merged with confirmed=false default.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            foldLatLonIntoLocation(db, "fuel_entries")
            foldLatLonIntoLocation(db, "expense_entries")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fuel_entries_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    odometer INTEGER NOT NULL,
                    gallons REAL NOT NULL,
                    cost REAL NOT NULL,
                    currency TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    photoUrl TEXT,
                    isPartialFill INTEGER NOT NULL,
                    economyIgnored INTEGER NOT NULL,
                    location TEXT,
                    notes TEXT,
                    tripType TEXT NOT NULL,
                    cloudManifest TEXT,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncId TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO fuel_entries_new (
                    id, vehicleId, odometer, gallons, cost, currency, timestamp,
                    photoUrl, isPartialFill, economyIgnored, location, notes, tripType,
                    cloudManifest, deleted, deletedAt, syncId, originDeviceId, updatedAt
                )
                SELECT
                    id, vehicleId, odometer, gallons, cost, currency, timestamp,
                    photoUrl, isPartialFill, economyIgnored, location, notes, tripType,
                    cloudManifest, deleted, deletedAt, syncId, originDeviceId, updatedAt
                FROM fuel_entries
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE fuel_entries")
            db.execSQL("ALTER TABLE fuel_entries_new RENAME TO fuel_entries")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS expense_entries_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    description TEXT NOT NULL,
                    vendor TEXT NOT NULL,
                    category TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    odometer INTEGER,
                    photoUrl TEXT,
                    location TEXT,
                    cloudManifest TEXT,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncId TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    vehicleSyncIdsJson TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO expense_entries_new (
                    id, vehicleId, amount, currency, description, vendor, category, date,
                    odometer, photoUrl, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt, vehicleSyncIdsJson
                )
                SELECT
                    id, vehicleId, amount, currency, description, vendor, category, date,
                    odometer, photoUrl, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt, vehicleSyncIdsJson
                FROM expense_entries
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE expense_entries")
            db.execSQL("ALTER TABLE expense_entries_new RENAME TO expense_entries")
        }

        private fun foldLatLonIntoLocation(db: SupportSQLiteDatabase, table: String) {
            db.query("SELECT id, latitude, longitude, location FROM $table").use { c ->
                val idIdx = c.getColumnIndex("id")
                val latIdx = c.getColumnIndex("latitude")
                val lonIdx = c.getColumnIndex("longitude")
                val locIdx = c.getColumnIndex("location")
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val lat = if (c.isNull(latIdx)) null else c.getDouble(latIdx)
                    val lon = if (c.isNull(lonIdx)) null else c.getDouble(lonIdx)
                    val loc = if (c.isNull(locIdx)) null else c.getString(locIdx)
                    val folded = FuelLocationJson.foldLegacy(lat, lon, loc)
                    db.execSQL(
                        "UPDATE $table SET location = ? WHERE id = ?",
                        arrayOf<Any?>(folded, id),
                    )
                }
            }
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS expense_entries_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    description TEXT NOT NULL,
                    vendor TEXT NOT NULL,
                    category TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    odometer INTEGER,
                    photoUrl TEXT,
                    latitude REAL,
                    longitude REAL,
                    location TEXT,
                    cloudManifest TEXT,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncId TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    vehicleSyncIdsJson TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO expense_entries_new (
                    id, vehicleId, amount, currency, description, vendor, category, date,
                    odometer, photoUrl, latitude, longitude, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt, vehicleSyncIdsJson
                )
                SELECT
                    id, vehicleId, amount, currency, description, vendor, category, date,
                    odometer, photoUrl, latitude, longitude, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt, vehicleSyncIdsJson
                FROM expense_entries
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE expense_entries")
            db.execSQL("ALTER TABLE expense_entries_new RENAME TO expense_entries")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fuel_entries_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    odometer INTEGER NOT NULL,
                    gallons REAL NOT NULL,
                    cost REAL NOT NULL,
                    currency TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    photoUrl TEXT,
                    isPartialFill INTEGER NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    location TEXT,
                    cloudManifest TEXT,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncId TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO fuel_entries_new (
                    id, vehicleId, odometer, gallons, cost, currency, timestamp,
                    photoUrl, isPartialFill, latitude, longitude, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt
                )
                SELECT
                    id, vehicleId, odometer, gallons, cost, currency, timestamp,
                    photoUrl, isPartialFill, latitude, longitude, location, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt
                FROM fuel_entries
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE fuel_entries")
            db.execSQL("ALTER TABLE fuel_entries_new RENAME TO fuel_entries")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vehicles_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    make TEXT,
                    model TEXT,
                    year INTEGER,
                    licensePlate TEXT,
                    vin TEXT,
                    notes TEXT,
                    referenceDashPhotoUrl TEXT,
                    cleanedReferenceDashPhotoUrl TEXT,
                    odometerCropLeft REAL,
                    odometerCropTop REAL,
                    odometerCropRight REAL,
                    odometerCropBottom REAL,
                    otherTextCropLeft REAL,
                    otherTextCropTop REAL,
                    otherTextCropRight REAL,
                    otherTextCropBottom REAL,
                    landmarkTextBlocksJson TEXT,
                    cloudManifest TEXT,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER,
                    syncId TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO vehicles_new (
                    id, name, make, model, year, licensePlate, vin, notes,
                    referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl,
                    odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom,
                    otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom,
                    landmarkTextBlocksJson, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt
                )
                SELECT
                    id, name, make, model, year, licensePlate, vin, notes,
                    referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl,
                    odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom,
                    otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom,
                    landmarkTextBlocksJson, cloudManifest,
                    deleted, deletedAt, syncId, originDeviceId, updatedAt
                FROM vehicles
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE vehicles")
            db.execSQL("ALTER TABLE vehicles_new RENAME TO vehicles")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE expense_entries ADD COLUMN vehicleSyncIdsJson TEXT NOT NULL DEFAULT ''",
            )
            db.execSQL(
                """
                UPDATE expense_entries
                SET vehicleSyncIdsJson = (
                    SELECT '["' || v.syncId || '"]'
                    FROM vehicles v
                    WHERE v.id = expense_entries.vehicleId
                      AND v.syncId != ''
                )
                WHERE vehicleSyncIdsJson = ''
                  AND vehicleId > 0
                  AND EXISTS (
                    SELECT 1 FROM vehicles v
                    WHERE v.id = expense_entries.vehicleId AND v.syncId != ''
                  )
                """.trimIndent(),
            )
        }
    }

    /**
     * v19: per-vehicle expense category catalog JSON (mirrors tripTypesJson).
     * Blank on existing rows → seed/inherit at next local insert or UI parse seed.
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE vehicles ADD COLUMN expenseCategoriesJson TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    /**
     * v20: per-vehicle odometer face digit count (default 6) and rollover count.
     * FuelEntry.odometer stores tracking miles including rollover encoding.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE vehicles ADD COLUMN odometerDigitCount INTEGER NOT NULL DEFAULT 6",
            )
            db.execSQL(
                "ALTER TABLE vehicles ADD COLUMN odometerRolloverCount INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /**
     * v21: join the two independent v20 lineages.
     * Branch v20 added odo digit/rollover; master v20 added known_stations.
     * This step is idempotent so either v20 install can upgrade.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            fun hasColumn(table: String, col: String): Boolean {
                db.query("PRAGMA table_info($table)").use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        if (c.getString(nameIdx) == col) return true
                    }
                }
                return false
            }
            if (!hasColumn("vehicles", "odometerDigitCount")) {
                db.execSQL(
                    "ALTER TABLE vehicles ADD COLUMN odometerDigitCount INTEGER NOT NULL DEFAULT 6",
                )
            }
            if (!hasColumn("vehicles", "odometerRolloverCount")) {
                db.execSQL(
                    "ALTER TABLE vehicles ADD COLUMN odometerRolloverCount INTEGER NOT NULL DEFAULT 0",
                )
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS known_stations (
                    syncId TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    address TEXT NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    accuracyM REAL,
                    kind TEXT NOT NULL,
                    source TEXT NOT NULL,
                    originDeviceId TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deleted INTEGER NOT NULL,
                    deletedAt INTEGER
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vehicle_expenses.db"
        )
        .addMigrations(
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
        )
        .fallbackToDestructiveMigration(BuildConfig.DEBUG)
        .build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideExpenseEntryDao(database: AppDatabase): ExpenseEntryDao = database.expenseEntryDao()

    @Provides
    fun provideFuelEntryDao(database: AppDatabase): FuelEntryDao = database.fuelEntryDao()

    @Provides
    fun provideMergeAckDao(database: AppDatabase): MergeAckDao = database.mergeAckDao()

    @Provides
    fun provideKnownStationDao(database: AppDatabase): KnownStationDao = database.knownStationDao()
}
