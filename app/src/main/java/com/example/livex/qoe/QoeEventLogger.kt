package com.example.livex.qoe

import com.example.livex.data.Stream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Turns the player's QoE state into discrete, structured [QoeEvent]s, one session per play request.
 *
 * Most events are derived by diffing consecutive [QoeMetrics] snapshots (from QoeTracker, which
 * already classifies startup / seek buffering / stalls), so there's one source of truth for "is
 * this a stall?": the overlay and the analytics can never disagree.
 *
 * Not thread-safe: call it from the player's thread (main). Pure Kotlin, time is passed in.
 */
class QoeEventLogger(
    private val emit: (QoeEvent) -> Unit,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The open session, or null between sessions. */
    var sessionId: String? = null
        private set

    private var startAtMs = 0L
    private var last = QoeMetrics()
    private var videoStarted = false
    private var hadFatalError = false
    private var stallTimeAtStallStart = 0L
    private var bitrateTimeProduct = 0.0
    private var bitrateTimeMs = 0L

    /** The play request. A session that's still open (player rebuild) ends as RESTARTED. */
    fun startSession(stream: Stream, nowMs: Long, epochMs: Long) {
        endSession(nowMs, EndReason.RESTARTED)
        val id = newSessionId()
        sessionId = id
        startAtMs = nowMs
        last = QoeMetrics()
        videoStarted = false
        hadFatalError = false
        bitrateTimeProduct = 0.0
        bitrateTimeMs = 0
        emit(QoeEvent.SessionStart(id, nowMs, stream.id, stream.title, stream.isLive, epochMs))
    }

    /** Every QoeTracker update (player events + the 500 ms tick). */
    fun onMetrics(metrics: QoeMetrics, nowMs: Long) {
        val id = sessionId ?: return
        val stalls = metrics.stalls

        // Average bitrate weighted by time actually PLAYING at each rendition (pauses and stalls
        // don't count): the bitrate that played up to now is the one before this update.
        val playedMs = stalls.playingTimeMs - last.stalls.playingTimeMs
        val playedBitrate = last.videoBitrateBps ?: metrics.videoBitrateBps
        if (playedMs > 0 && playedBitrate != null) {
            bitrateTimeProduct += playedBitrate.toDouble() * playedMs
            bitrateTimeMs += playedMs
        }

        val vst = stalls.startupTimeMs
        if (!videoStarted && vst != null) {
            videoStarted = true
            emit(QoeEvent.VideoStart(id, nowMs, vst))
        }
        if (metrics.qualitySwitches > last.qualitySwitches) {
            emit(
                QoeEvent.BitrateSwitch(
                    id, nowMs, last.videoBitrateBps, metrics.videoBitrateBps, metrics.videoWidth, metrics.videoHeight,
                ),
            )
        }
        if (stalls.isStalled && !last.stalls.isStalled) {
            stallTimeAtStallStart = last.stalls.stallTimeMs
            emit(QoeEvent.StallStart(id, nowMs))
        } else if (!stalls.isStalled && last.stalls.isStalled) {
            emit(QoeEvent.StallEnd(id, nowMs, stalls.stallTimeMs - stallTimeAtStallStart))
        }
        if (metrics.loadErrorCount > last.loadErrorCount) {
            val failed = metrics.loadErrorCount - last.loadErrorCount
            emit(
                QoeEvent.PlaybackError(
                    id, nowMs, "LOAD_ERROR",
                    "$failed request(s) failed, retrying (${metrics.loadErrorCount} so far)",
                    isFatal = false,
                ),
            )
        }
        last = metrics
    }

    fun onAdEvent(name: String, info: String?, isError: Boolean, nowMs: Long) {
        val id = sessionId ?: return
        emit(QoeEvent.Ad(id, nowMs, name, info, isError))
    }

    /** onPlayerError: playback stopped. The session stays open (the viewer may retry). */
    fun onPlayerError(code: String, message: String, nowMs: Long) {
        val id = sessionId ?: return
        hadFatalError = true
        emit(QoeEvent.PlaybackError(id, nowMs, code, message, isFatal = true))
    }

    fun endSession(nowMs: Long, reason: EndReason) {
        val id = sessionId ?: return
        val stalls = last.stalls
        if (stalls.isStalled) emit(QoeEvent.StallEnd(id, nowMs, stalls.stallTimeMs - stallTimeAtStallStart))
        // EBVS only when the VIEWER gave up: a failed start is a start failure, a rebuild isn't leaving.
        if (reason == EndReason.EXIT && !videoStarted && !hadFatalError) {
            emit(QoeEvent.Ebvs(id, nowMs, nowMs - startAtMs))
        }
        val avgBitrate = if (bitrateTimeMs > 0) (bitrateTimeProduct / bitrateTimeMs).roundToInt() else null
        emit(QoeEvent.SessionEnd(id, nowMs, reason, stalls.playingTimeMs, stalls.stallTimeMs, avgBitrate))
        sessionId = null
    }
}
