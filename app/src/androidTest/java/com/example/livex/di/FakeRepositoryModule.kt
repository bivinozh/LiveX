package com.example.livex.di

import com.example.livex.data.greeting.GreetingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

const val FAKE_GREETING = "Hello from fake!"

/** Replaces [RepositoryModule] in every instrumented test with fast, deterministic fakes. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
object FakeRepositoryModule {

    @Provides
    @Singleton
    fun provideGreetingRepository(): GreetingRepository = object : GreetingRepository {
        override suspend fun getGreeting() = FAKE_GREETING
    }
}
