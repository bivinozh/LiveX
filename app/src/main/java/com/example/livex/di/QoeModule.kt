package com.example.livex.di

import android.content.Context
import androidx.room.Room
import com.example.livex.qoe.FakeQoeReporter
import com.example.livex.qoe.QoeReporter
import com.example.livex.qoe.store.LiveXDatabase
import com.example.livex.qoe.store.QoeDao
import com.example.livex.qoe.store.QoeSessionRepository
import com.example.livex.qoe.store.RoomQoeSessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QoeModule {

    @Binds
    abstract fun bindQoeSessionRepository(impl: RoomQoeSessionRepository): QoeSessionRepository

    /**
     * Analytics backends. A real one is one more @IntoSet binding, e.g. ConvivaQoeReporter or
     * MuxQoeReporter, with its key from BuildConfig / remote config (never committed).
     */
    @Binds
    @IntoSet
    abstract fun bindFakeQoeReporter(impl: FakeQoeReporter): QoeReporter

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): LiveXDatabase =
            Room.databaseBuilder(context, LiveXDatabase::class.java, "livex.db").build()

        @Provides
        fun provideQoeDao(database: LiveXDatabase): QoeDao = database.qoeDao()
    }
}
