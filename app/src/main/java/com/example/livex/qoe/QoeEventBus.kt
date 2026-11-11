package com.example.livex.qoe

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide stream of [QoeEvent]s. Producers (one QoeEventLogger per player) and consumers (Room,
 * analytics reporters, via QoePipeline) don't know about each other.
 *
 * A SharedFlow, not a StateFlow: every event matters (a StateFlow would conflate two quick events
 * into one). [emit] never suspends and never blocks the player thread: if consumers fall behind by
 * more than [BUFFER] events, the oldest are dropped rather than stalling playback.
 */
@Singleton
class QoeEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<QoeEvent>(
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<QoeEvent> = _events.asSharedFlow()

    fun emit(event: QoeEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val BUFFER = 256
    }
}
