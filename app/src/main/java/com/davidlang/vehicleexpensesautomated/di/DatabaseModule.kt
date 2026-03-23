package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelFillDao
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.local.FuelDao
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
        ).build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideFuelFillDao(database: AppDatabase): FuelFillDao = database.fuelFillDao()

    @Provides
    fun provideFuelDao(fuelFillDao: FuelFillDao): FuelDao = fuelFillDao as FuelDao
}
