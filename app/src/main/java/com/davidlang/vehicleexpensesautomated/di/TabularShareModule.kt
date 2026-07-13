package com.davidlang.vehicleexpensesautomated.di

import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApi
import com.davidlang.vehicleexpensesautomated.data.sync.tabular.TabularShareApiImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TabularShareModule {
    @Binds
    @Singleton
    abstract fun bindTabularShareApi(impl: TabularShareApiImpl): TabularShareApi
}