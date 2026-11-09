package com.example.livex.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.livex.settings.AbrSettingsRepository
import com.example.livex.settings.BufferSettingsRepository
import com.example.livex.settings.DataStoreAbrSettingsRepository
import com.example.livex.settings.DataStoreBufferSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindBufferSettingsRepository(impl: DataStoreBufferSettingsRepository): BufferSettingsRepository

    @Binds
    abstract fun bindAbrSettingsRepository(impl: DataStoreAbrSettingsRepository): AbrSettingsRepository

    companion object {
        /** Exactly one DataStore per file per process (a second instance on the same file throws). */
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("buffer_settings") })
    }
}
