package com.example.livex.qoe

import androidx.media3.common.Player

/**
 * Pure QoE bookkeeping (no Android, no ExoPlayer instance) so it can be unit-tested.
 * Timestamps are passed in explicitly, in elapsed-realtime milliseconds.
 *
 * Startup, stall and seek-buffering classification is delegated to [StallTracker].
 */
class QoeCalculator {

    private val stallTracker = StallTracker()
    private var base = QoeMetrics()
    private var lastNowMs = 0L

    /** Current metrics; ongoing stall/playing time is included up to the latest event or tick. */
    val metrics: QoeMetrics
        get() = base.copy(stalls = stallTracker.snapshot(lastNowMs))

    /** Call right before prepare() (the play request): starts a fresh measurement. */
    fun reset(nowMs: Long) {
        base = QoeMetrics()
        lastNowMs = nowMs
        stallTracker.reset(nowMs)
    }

    fun onFirstFrameRendered(nowMs: Long) = at(nowMs) { stallTracker.onFirstFrameRendered(nowMs) }

    fun onPlaybackStateChanged(state: Int, nowMs: Long) = at(nowMs) {
        base = base.copy(playbackState = stateName(state))
        stallTracker.onPlaybackStateChanged(state, nowMs)
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean, nowMs: Long) =
        at(nowMs) { stallTracker.onPlayWhenReadyChanged(playWhenReady, nowMs) }

    fun onSeek(nowMs: Long) = at(nowMs) { stallTracker.onSeek(nowMs) }

    /** Periodic sample (every 500 ms): buffer level, and lets ongoing durations grow. */
    fun onTick(bufferedDurationMs: Long, nowMs: Long) = at(nowMs) {
        base = base.copy(bufferedDurationMs = bufferedDurationMs)
    }

    fun onVideoFormatChanged(width: Int, height: Int, bitrateBps: Int?) {
        val isSwitch = base.videoWidth > 0 &&
            (base.videoWidth != width || base.videoHeight != height || base.videoBitrateBps != bitrateBps)
        base = base.copy(
            videoWidth = width,
            videoHeight = height,
            videoBitrateBps = bitrateBps,
            qualitySwitches = base.qualitySwitches + if (isSwitch) 1 else 0,
        )
    }

    fun onDroppedFrames(count: Int) {
        base = base.copy(droppedFrames = base.droppedFrames + count)
    }

    fun onLoadCompleted(bytesLoaded: Long, loadDurationMs: Long) {
        base = base.copy(
            requestCount = base.requestCount + 1,
            bytesLoaded = base.bytesLoaded + bytesLoaded,
            totalLoadTimeMs = base.totalLoadTimeMs + loadDurationMs,
        )
    }

    fun onLoadError() {
        base = base.copy(loadErrorCount = base.loadErrorCount + 1)
    }

    fun onBandwidthEstimate(bitrateBps: Long) {
        base = base.copy(bandwidthEstimateBps = bitrateBps)
    }

    private inline fun at(nowMs: Long, block: () -> Unit) {
        lastNowMs = maxOf(lastNowMs, nowMs)
        block()
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "Idle"
        Player.STATE_BUFFERING -> "Buffering"
        Player.STATE_READY -> "Ready"
        Player.STATE_ENDED -> "Ended"
        else -> "Unknown"
    }
}
