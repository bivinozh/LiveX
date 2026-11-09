package com.example.livex.qoe

import androidx.media3.common.Player

/**
 * Pure QoE bookkeeping (no Android, no ExoPlayer instance) so it can be unit-tested.
 * Timestamps are passed in explicitly, in elapsed-realtime milliseconds.
 */
class QoeCalculator {

    var metrics = QoeMetrics()
        private set

    private var prepareAtMs: Long? = null
    private var hasStarted = false
    private var rebufferStartMs: Long? = null

    /** Call right before prepare(): starts a fresh measurement. */
    fun reset(nowMs: Long) {
        metrics = QoeMetrics()
        prepareAtMs = nowMs
        hasStarted = false
        rebufferStartMs = null
    }

    fun onFirstFrameRendered(nowMs: Long) {
        hasStarted = true
        if (metrics.startupTimeMs == null) {
            prepareAtMs?.let { metrics = metrics.copy(startupTimeMs = nowMs - it) }
        }
    }

    fun onPlaybackStateChanged(state: Int, nowMs: Long) {
        var updated = metrics.copy(playbackState = stateName(state))
        when (state) {
            // Buffering before the first frame is startup, not a rebuffer.
            Player.STATE_BUFFERING -> if (hasStarted && rebufferStartMs == null) {
                rebufferStartMs = nowMs
                updated = updated.copy(rebufferCount = updated.rebufferCount + 1)
            }
            Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> rebufferStartMs?.let { start ->
                updated = updated.copy(rebufferTimeMs = updated.rebufferTimeMs + (nowMs - start))
                rebufferStartMs = null
            }
        }
        metrics = updated
    }

    fun onVideoFormatChanged(width: Int, height: Int, bitrateBps: Int?) {
        val isSwitch = metrics.videoWidth > 0 &&
            (metrics.videoWidth != width || metrics.videoHeight != height || metrics.videoBitrateBps != bitrateBps)
        metrics = metrics.copy(
            videoWidth = width,
            videoHeight = height,
            videoBitrateBps = bitrateBps,
            qualitySwitches = metrics.qualitySwitches + if (isSwitch) 1 else 0,
        )
    }

    fun onDroppedFrames(count: Int) {
        metrics = metrics.copy(droppedFrames = metrics.droppedFrames + count)
    }

    fun onLoadCompleted(bytesLoaded: Long, loadDurationMs: Long) {
        metrics = metrics.copy(
            requestCount = metrics.requestCount + 1,
            bytesLoaded = metrics.bytesLoaded + bytesLoaded,
            totalLoadTimeMs = metrics.totalLoadTimeMs + loadDurationMs,
        )
    }

    fun onLoadError() {
        metrics = metrics.copy(loadErrorCount = metrics.loadErrorCount + 1)
    }

    fun onBandwidthEstimate(bitrateBps: Long) {
        metrics = metrics.copy(bandwidthEstimateBps = bitrateBps)
    }

    fun onBufferedDuration(durationMs: Long) {
        metrics = metrics.copy(bufferedDurationMs = durationMs)
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "Idle"
        Player.STATE_BUFFERING -> "Buffering"
        Player.STATE_READY -> "Ready"
        Player.STATE_ENDED -> "Ended"
        else -> "Unknown"
    }
}
