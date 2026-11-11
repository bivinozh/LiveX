package com.example.livex.di

import com.example.livex.device.AndroidDeviceInfoReader
import com.example.livex.device.DeviceInfoReader
import com.example.livex.playback.AndroidDeviceCapabilities
import com.example.livex.playback.DeviceCapabilities
import com.example.livex.player.ExoPlaybackEngine
import com.example.livex.player.PlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    /**
     * App-wide: the PlayerViewModel (UI) and the PlaybackService (MediaSession, notification) must
     * see the SAME player. Its playback lifetime is still driven by the player screen: start() when
     * it opens, release() when it's closed for good.
     */
    @Binds
    @Singleton
    abstract fun bindPlaybackEngine(impl: ExoPlaybackEngine): PlaybackEngine

    @Binds
    @Singleton
    abstract fun bindDeviceCapabilities(impl: AndroidDeviceCapabilities): DeviceCapabilities

    @Binds
    abstract fun bindDeviceInfoReader(impl: AndroidDeviceInfoReader): DeviceInfoReader
}
