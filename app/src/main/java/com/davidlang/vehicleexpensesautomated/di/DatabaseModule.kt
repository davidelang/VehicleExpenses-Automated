package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import com.davidlang.vehicleexpensesautomated.data.db.AppDatabase
import com.davidlang.vehicleexpensesautomated.data.dao.VehicleDao
import com.davidlang.vehicleexpensesautomated.data.dao.ExpenseDao
import com.davidlang.vehicleexpensesautomated.data.dao.FuelFillDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vehicle_expenses_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    @Singleton
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    @Singleton
    fun provideFuelFillDao(db: AppDatabase): FuelFillDao = db.fuelFillDao()
}
