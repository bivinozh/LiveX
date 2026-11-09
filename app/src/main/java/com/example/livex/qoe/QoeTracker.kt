package com.example.livex.qoe

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** Bridges ExoPlayer analytics events into [QoeCalculator] and publishes the result. */
@OptIn(UnstableApi::class)
class QoeTracker : AnalyticsListener {

    private val calculator = QoeCalculator()
    private val _metrics = MutableStateFlow(QoeMetrics())
    val metrics: StateFlow<QoeMetrics> = _metrics.asStateFlow()

    fun reset(nowMs: Long) = update { reset(nowMs) }

    /** Periodic sample from the engine's 500 ms loop. */
    fun onTick(bufferedDurationMs: Long, nowMs: Long) = update { onTick(bufferedDurationMs, nowMs) }

    override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) =
        update { onPlayWhenReadyChanged(playWhenReady, eventTime.realtimeMs) }

    /** Only user/app seeks matter here; other discontinuities (auto-transition, ad insertion...) are ignored. */
    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) update { onSeek(eventTime.realtimeMs) }
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) =
        update { onPlaybackStateChanged(state, eventTime.realtimeMs) }

    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) =
        update { onFirstFrameRendered(eventTime.realtimeMs) }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) = update {
        onVideoFormatChanged(format.width, format.height, format.bitrate.takeIf { it != Format.NO_VALUE })
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) =
        update { onDroppedFrames(droppedFrames) }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) = update { onLoadCompleted(loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs) }

    /**
     * A segment/manifest request failed. Not fatal by itself: the LoadErrorHandlingPolicy retries
     * (with backoff), and only exhausted retries become onPlayerError.
     */
    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) = update { onLoadError() }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) = update { onBandwidthEstimate(bitrateEstimate) }

    private inline fun update(block: QoeCalculator.() -> Unit) {
        calculator.block()
        _metrics.value = calculator.metrics
    }
}
