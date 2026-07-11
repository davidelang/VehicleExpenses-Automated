package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vehicle_expenses.db"
        )
        .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .fallbackToDestructiveMigration(true)
        .build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideExpenseEntryDao(database: AppDatabase): ExpenseEntryDao = database.expenseEntryDao()

    @Provides
    fun provideFuelEntryDao(database: AppDatabase): FuelEntryDao = database.fuelEntryDao()
}
