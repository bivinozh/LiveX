package com.example.livex.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** Buffer durations passed to DefaultLoadControl.Builder.setBufferDurationsMs. */
data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    companion object {
        /** Media3's own defaults, read from the library so the guide always shows the real values. */
        val Default = media3Defaults()

        /** Start sooner and hold less media in memory, at the cost of less protection against network dips. */
        val Tuned = BufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 500,
            bufferForPlaybackAfterRebufferMs = 1_500,
        )
    }
}

@OptIn(UnstableApi::class)
private fun media3Defaults() = BufferProfile(
    minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
    maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
    bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
)

/** Track-selection cap used by the "data saver" ABR optimization. */
object AbrProfile {
    const val DATA_SAVER_MAX_WIDTH = 1280
    const val DATA_SAVER_MAX_HEIGHT = 720
}
