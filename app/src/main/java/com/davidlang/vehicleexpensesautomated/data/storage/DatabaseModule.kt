package com.davidlang.vehicleexpensesautomated.data.storage

import android.content.Context
import androidx.room.Room
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle
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
            .addMigrations(AppDatabase.MIGRATION_1_2)   // ← your referenceDashPhotoUrl migration
            .fallbackToDestructiveMigration()           // safe for dev only (remove in production)
            .build()
    }

    @Provides
    fun provideVehicleDao(database: AppDatabase): VehicleDao = database.vehicleDao()
}
