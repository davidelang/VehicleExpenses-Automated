package com.davidlang.vehicleexpensesautomated.di

import android.content.Context
import androidx.room.Room
import com.davidlang.vehicleexpensesautomated.data.local.AppDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vehicle_expenses.db"
        ).build()
    }

    @Provides
    fun provideFuelDao(database: AppDatabase): FuelDao = database.fuelDao()
}
