package com.example.livex.di

import com.example.livex.data.greeting.DefaultGreetingRepository
import com.example.livex.data.greeting.GreetingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGreetingRepository(impl: DefaultGreetingRepository): GreetingRepository
}
