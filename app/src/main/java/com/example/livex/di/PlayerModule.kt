package com.example.livex.di

import com.example.livex.player.ExoPlaybackEngine
import com.example.livex.player.PlaybackEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class PlayerModule {

    /** One engine (and so one ExoPlayer) per ViewModel. */
    @Binds
    @ViewModelScoped
    abstract fun bindPlaybackEngine(impl: ExoPlaybackEngine): PlaybackEngine
}
