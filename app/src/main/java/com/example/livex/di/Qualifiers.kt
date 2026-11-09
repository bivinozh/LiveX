package com.example.livex.di

import javax.inject.Qualifier

/** The app's HTTP User-Agent, shared by ExoPlayer's data sources and plain OkHttp requests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserAgent

/** Dispatcher for blocking I/O (network, disk). Injected so tests can replace it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
