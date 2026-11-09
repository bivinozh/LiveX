package com.example.livex.qoe

import java.util.Locale

/**
 * Snapshot shown by the stats overlay, rebuilt every 500 ms.
 *
 * Two kinds of input:
 * - Event-driven ([QoeMetrics], from AnalyticsListener): resolution, bitrate, bandwidth estimate,
 *   dropped frames, load errors, player state. These change only when something happens.
 * - Polled ([PlayerSample], read from the Player): buffered duration, live offset, speed. These
 *   change continuously and have no callback, so they're sampled on a timer.
 */
data class StatsState(
    val playerState: String = "Idle",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Declared bitrate of the video rendition being decoded (from the manifest). */
    val videoBitrateBps: Int? = null,
    /** ExoPlayer's network throughput estimate, the main input to ABR. */
    val bandwidthEstimateBps: Long? = null,
    /** Media loaded ahead of the playhead (Player.totalBufferedDuration). */
    val bufferedDurationMs: Long = 0,
    /** Distance behind the live edge (Player.currentLiveOffset); null for VOD. */
    val liveOffsetMs: Long? = null,
    val playbackSpeed: Float = 1f,
    val droppedFrames: Int = 0,
    val loadErrorCount: Int = 0,
) {
    data class Row(val label: String, val value: String)

    fun rows(): List<Row> = listOf(
        Row(STATE, playerState),
        Row(RESOLUTION, if (videoWidth > 0) "$videoWidth×$videoHeight" else NONE),
        Row(VIDEO_BITRATE, videoBitrateBps?.let { mbps(it.toLong()) } ?: NONE),
        Row(BANDWIDTH, bandwidthEstimateBps?.let(::mbps) ?: NONE),
        Row(BUFFER, seconds(bufferedDurationMs)),
        Row(LIVE_OFFSET, liveOffsetText()),
        Row(SPEED, String.format(Locale.US, "%.2f×", playbackSpeed)),
        Row(DROPPED_FRAMES, "$droppedFrames"),
        Row(LOAD_ERRORS, "$loadErrorCount"),
    )

    /**
     * currentLiveOffset = device wall clock - wall-clock time of the playhead (HLS PROGRAM-DATE-TIME).
     * Playback can't be ahead of the live edge, so a negative value means the device clock is wrong
     * (DASH can correct this with a UTCTiming element; HLS has no equivalent).
     */
    private fun liveOffsetText(): String = when {
        liveOffsetMs == null -> "$NONE (VOD)"
        liveOffsetMs < 0 -> CLOCK_SKEW
        else -> seconds(liveOffsetMs)
    }

    companion object {
        const val CLOCK_SKEW = "⚠ device clock off"
        const val STATE = "Player state"
        const val RESOLUTION = "Resolution"
        const val VIDEO_BITRATE = "Video bitrate"
        const val BANDWIDTH = "Est. bandwidth"
        const val BUFFER = "Buffer ahead"
        const val LIVE_OFFSET = "Live offset"
        const val SPEED = "Playback speed"
        const val DROPPED_FRAMES = "Dropped frames"
        const val LOAD_ERRORS = "Load errors"

        private const val NONE = "—"

        fun from(metrics: QoeMetrics, sample: PlayerSample) = StatsState(
            playerState = metrics.playbackState,
            videoWidth = metrics.videoWidth,
            videoHeight = metrics.videoHeight,
            videoBitrateBps = metrics.videoBitrateBps,
            bandwidthEstimateBps = metrics.bandwidthEstimateBps,
            bufferedDurationMs = sample.bufferedDurationMs,
            liveOffsetMs = sample.liveOffsetMs,
            playbackSpeed = sample.playbackSpeed,
            droppedFrames = metrics.droppedFrames,
            loadErrorCount = metrics.loadErrorCount,
        )

        private fun mbps(bps: Long) = String.format(Locale.US, "%.2f Mbps", bps / 1_000_000.0)
        private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)
    }
}

/** Values that have no callback and must be read from the Player on each tick. */
data class PlayerSample(
    val bufferedDurationMs: Long,
    val liveOffsetMs: Long?,
    val playbackSpeed: Float,
)
