package com.example.livex.player.live

import androidx.media3.common.MediaItem

/**
 * Target latency presets for live streams, applied as MediaItem.LiveConfiguration.
 *
 * targetOffsetMs: how far behind the live edge the player aims to play.
 * min/maxPlaybackSpeed: the range DefaultLivePlaybackSpeedControl may use to drift back to the
 * target (speed up when too far behind, slow down when too close). A few percent is inaudible.
 */
enum class LiveLatency(
    val label: String,
    val targetOffsetMs: Long,
    val minPlaybackSpeed: Float,
    val maxPlaybackSpeed: Float,
    val description: String,
) {
    LOW(
        label = "3 s",
        targetOffsetMs = 3_000,
        minPlaybackSpeed = 0.95f,
        maxPlaybackSpeed = 1.05f,
        description = "Close to real time (sports, betting, chat). Only ~1 segment of buffer: any hiccup stalls.",
    ),
    STANDARD(
        label = "10 s",
        targetOffsetMs = 10_000,
        minPlaybackSpeed = 0.97f,
        maxPlaybackSpeed = 1.03f,
        description = "Balanced: a few segments of cushion, still well ahead of classic broadcast-style HLS.",
    ),
    HIGH(
        label = "30 s",
        targetOffsetMs = 30_000,
        minPlaybackSpeed = 0.97f,
        maxPlaybackSpeed = 1.03f,
        description = "Classic HLS latency: a deep buffer rides out network trouble, but you're 30 s behind.",
    ),
    ;

    fun liveConfiguration(): MediaItem.LiveConfiguration = MediaItem.LiveConfiguration.Builder()
        .setTargetOffsetMs(targetOffsetMs)
        .setMinPlaybackSpeed(minPlaybackSpeed)
        .setMaxPlaybackSpeed(maxPlaybackSpeed)
        .build()

    companion object {
        val DEFAULT = STANDARD
    }
}
