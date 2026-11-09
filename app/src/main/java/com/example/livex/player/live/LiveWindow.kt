package com.example.livex.player.live

import java.util.Locale
import kotlin.math.abs

/**
 * The seekable live (DVR) window, sampled from the player every 500 ms. Positions are relative to
 * the window start, like Player.currentPosition.
 *
 * "Behind live" is measured against the window's default position (where GO LIVE /
 * seekToDefaultPosition lands: live edge minus the target offset), so it doesn't depend on the
 * device clock, unlike Player.currentLiveOffset.
 */
data class LiveWindow(
    val windowDurationMs: Long,
    val positionMs: Long,
    /** Live edge minus the target offset: where GO LIVE jumps to. */
    val defaultPositionMs: Long,
    /** The resolved target offset (MediaItem LiveConfiguration merged with the manifest's). */
    val targetOffsetMs: Long?,
) {
    val behindLiveMs: Long get() = (defaultPositionMs - positionMs).coerceAtLeast(0)

    /** Within one tolerance step of the target counts as live (the speed control hovers around it). */
    val isAtLiveEdge: Boolean get() = behindLiveMs <= LIVE_TOLERANCE_MS

    val statusText: String
        get() = if (isAtLiveEdge) "● LIVE" else String.format(Locale.US, "−%.1f s behind live", behindLiveMs / 1000.0)

    val positionFraction: Float get() = fraction(positionMs)
    val liveEdgeFraction: Float get() = fraction(defaultPositionMs)

    val windowLabel: String
        get() {
            val seconds = windowDurationMs / 1000
            return if (seconds < 120) "DVR window $seconds s" else "DVR window %d:%02d".format(seconds / 60, abs(seconds % 60))
        }

    private fun fraction(ms: Long): Float =
        if (windowDurationMs <= 0) 0f else (ms.toFloat() / windowDurationMs).coerceIn(0f, 1f)

    companion object {
        const val LIVE_TOLERANCE_MS = 1_500L
    }
}
