package com.example.livex.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

private const val HTTP_CACHE_SIZE_BYTES = 50L * 1024 * 1024

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** One app-wide client: its connection pool and disk cache are shared across player instances. */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_SIZE_BYTES))
            .build()
}
