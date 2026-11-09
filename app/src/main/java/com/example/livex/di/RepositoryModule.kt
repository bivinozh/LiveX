package com.example.livex.di

import com.example.livex.data.manifest.ManifestRepository
import com.example.livex.data.manifest.OkHttpManifestRepository
import com.example.livex.data.stream.DefaultStreamRepository
import com.example.livex.data.stream.StreamRepository
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
    abstract fun bindStreamRepository(impl: DefaultStreamRepository): StreamRepository

    @Binds
    @Singleton
    abstract fun bindManifestRepository(impl: OkHttpManifestRepository): ManifestRepository
}
