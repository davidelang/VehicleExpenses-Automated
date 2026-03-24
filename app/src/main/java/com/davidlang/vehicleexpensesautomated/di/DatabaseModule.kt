package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelEntryDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.db.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vehicle_expenses.db"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideExpenseEntryDao(database: AppDatabase): ExpenseEntryDao = database.expenseEntryDao()

    @Provides
    fun provideFuelEntryDao(database: AppDatabase): FuelEntryDao = database.fuelEntryDao()
}
