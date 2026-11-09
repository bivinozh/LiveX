package com.example.livex.di

import androidx.media3.common.MimeTypes
import com.example.livex.data.Stream
import com.example.livex.data.manifest.ManifestRepository
import com.example.livex.data.stream.StreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

val FAKE_STREAM = Stream(
    id = "fake",
    title = "Fake stream from test module",
    url = "https://example.invalid/fake.m3u8",
    mimeType = MimeTypes.APPLICATION_M3U8,
    isLive = false,
)

const val FAKE_MANIFEST = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg0.ts\n#EXT-X-ENDLIST"

/** Replaces [RepositoryModule] in every instrumented test with fast, deterministic fakes (no network). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
object FakeRepositoryModule {

    @Provides
    @Singleton
    fun provideStreamRepository(): StreamRepository = object : StreamRepository {
        override suspend fun getStreams() = listOf(FAKE_STREAM)
    }

    @Provides
    @Singleton
    fun provideManifestRepository(): ManifestRepository = object : ManifestRepository {
        override suspend fun fetch(url: String) = FAKE_MANIFEST
    }
}
