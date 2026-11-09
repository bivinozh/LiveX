package com.example.livex.qoe

import androidx.media3.common.Player

/**
 * Classifies every moment of a session so buffering is blamed correctly.
 *
 * - Startup (VST, video start time): play request -> first rendered frame. Buffering before the
 *   first frame belongs here.
 * - Seek buffering: buffering that starts right after the viewer seeks. They asked for content
 *   that isn't downloaded yet, so waiting is expected, not a failure.
 * - Stall (rebuffer): buffering after playback started, not caused by a seek, while the viewer
 *   wants to watch (playWhenReady). The buffer ran dry: this is the QoE failure.
 *
 * Time is split into phases (startup / playing / stalled / seek-buffering / not watching) and each
 * phase's duration is accumulated at every transition, so totals are exact and an ongoing phase
 * can be included in [snapshot] without waiting for it to end.
 *
 * Pure Kotlin: all timestamps are passed in (elapsed-realtime ms), so it's unit-tested on the JVM.
 */
class StallTracker {

    private enum class Phase { IDLE, STARTUP, PLAYING, STALL, SEEK_BUFFERING, NOT_WATCHING }

    /** What the current buffering episode is attributed to (decided when buffering starts). */
    private enum class Cause { STARTUP, SEEK, STALL }

    private var playbackState = Player.STATE_IDLE
    private var playWhenReady = false
    private var playRequestAtMs: Long? = null
    private var startupTimeMs: Long? = null
    private var lastSeekAtMs: Long? = null

    private var bufferingCause: Cause? = null
    private var bufferingCounted = false

    private var phase = Phase.IDLE
    private var phaseStartMs = 0L

    private var stallCount = 0
    private var stallTimeMs = 0L
    private var seekBufferingCount = 0
    private var seekBufferingTimeMs = 0L
    private var playingTimeMs = 0L

    /** The play request: call right before prepare(). Starts a fresh measurement. */
    fun reset(nowMs: Long) {
        playbackState = Player.STATE_IDLE
        playWhenReady = false
        playRequestAtMs = nowMs
        startupTimeMs = null
        lastSeekAtMs = null
        bufferingCause = null
        bufferingCounted = false
        stallCount = 0
        stallTimeMs = 0
        seekBufferingCount = 0
        seekBufferingTimeMs = 0
        playingTimeMs = 0
        phase = currentPhase()
        phaseStartMs = nowMs
    }

    /** From onRenderedFirstFrame: ends startup (measured once per play request). */
    fun onFirstFrameRendered(nowMs: Long) = transition(nowMs) {
        val requestedAt = playRequestAtMs
        if (startupTimeMs == null && requestedAt != null) startupTimeMs = nowMs - requestedAt
    }

    /** From onPositionDiscontinuity(reason = DISCONTINUITY_REASON_SEEK). */
    fun onSeek(nowMs: Long) = transition(nowMs) {
        lastSeekAtMs = nowMs
        // Seeking while already buffering: whatever was loading is abandoned, the new wait is the seek's.
        if (playbackState == Player.STATE_BUFFERING && startupTimeMs != null) startBuffering(Cause.SEEK)
    }

    fun onPlaybackStateChanged(state: Int, nowMs: Long) = transition(nowMs) {
        if (state == Player.STATE_BUFFERING && playbackState != Player.STATE_BUFFERING) {
            startBuffering(
                when {
                    startupTimeMs == null -> Cause.STARTUP
                    isRightAfterSeek(nowMs) -> Cause.SEEK
                    else -> Cause.STALL
                },
            )
        }
        if (state != Player.STATE_BUFFERING) bufferingCause = null
        if (state == Player.STATE_READY) lastSeekAtMs = null // the seek has been served
        playbackState = state
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean, nowMs: Long) = transition(nowMs) {
        this.playWhenReady = playWhenReady
    }

    fun snapshot(nowMs: Long): StallStats {
        val ongoing = nowMs - phaseStartMs
        return StallStats(
            startupTimeMs = startupTimeMs,
            stallCount = stallCount,
            stallTimeMs = stallTimeMs + if (phase == Phase.STALL) ongoing else 0,
            seekBufferingCount = seekBufferingCount,
            seekBufferingTimeMs = seekBufferingTimeMs + if (phase == Phase.SEEK_BUFFERING) ongoing else 0,
            playingTimeMs = playingTimeMs + if (phase == Phase.PLAYING) ongoing else 0,
            isStalled = phase == Phase.STALL,
        )
    }

    private fun startBuffering(cause: Cause) {
        bufferingCause = cause
        bufferingCounted = false
    }

    /**
     * A seek normally goes straight to BUFFERING and is cleared at READY. The time limit covers
     * seeks inside the buffer that never leave READY: they must not excuse a stall minutes later.
     */
    private fun isRightAfterSeek(nowMs: Long): Boolean =
        lastSeekAtMs?.let { nowMs - it <= SEEK_ATTRIBUTION_WINDOW_MS } == true

    private inline fun transition(nowMs: Long, change: () -> Unit) {
        accumulate(nowMs)
        change()
        phase = currentPhase()
        phaseStartMs = nowMs
        // Count an episode the first time the viewer actually waits on it (not while paused),
        // and only once even if they pause/resume during the same episode.
        if ((phase == Phase.STALL || phase == Phase.SEEK_BUFFERING) && !bufferingCounted) {
            bufferingCounted = true
            if (phase == Phase.STALL) stallCount++ else seekBufferingCount++
        }
    }

    private fun accumulate(nowMs: Long) {
        val elapsed = nowMs - phaseStartMs
        when (phase) {
            Phase.PLAYING -> playingTimeMs += elapsed
            Phase.STALL -> stallTimeMs += elapsed
            Phase.SEEK_BUFFERING -> seekBufferingTimeMs += elapsed
            Phase.IDLE, Phase.STARTUP, Phase.NOT_WATCHING -> Unit
        }
    }

    private fun currentPhase(): Phase = when {
        startupTimeMs == null -> if (playRequestAtMs != null) Phase.STARTUP else Phase.IDLE
        !playWhenReady -> Phase.NOT_WATCHING // paused: any buffering isn't felt by the viewer
        playbackState == Player.STATE_READY -> Phase.PLAYING
        playbackState == Player.STATE_BUFFERING -> when (bufferingCause) {
            Cause.SEEK -> Phase.SEEK_BUFFERING
            Cause.STALL -> Phase.STALL
            // Still finishing startup buffering after an early first frame: not a stall.
            Cause.STARTUP, null -> Phase.NOT_WATCHING
        }
        else -> Phase.NOT_WATCHING // IDLE (e.g. after an error) or ENDED
    }

    companion object {
        /** Buffering must start within this time of a seek to be blamed on it. */
        const val SEEK_ATTRIBUTION_WINDOW_MS = 2_000L
    }
}

data class StallStats(
    /** VST: play request -> first frame. Null until the first frame. */
    val startupTimeMs: Long? = null,
    val stallCount: Int = 0,
    val stallTimeMs: Long = 0,
    val seekBufferingCount: Int = 0,
    val seekBufferingTimeMs: Long = 0,
    /** Time actually playing (READY + playWhenReady); excludes startup, seeks and pauses. */
    val playingTimeMs: Long = 0,
    val isStalled: Boolean = false,
) {
    /**
     * Rebuffering ratio = stall time / (playing time + stall time): the share of the time the viewer
     * wanted to watch that they spent staring at a spinner instead. Null until something was watched.
     */
    val rebufferingRatio: Double?
        get() {
            val watchTime = playingTimeMs + stallTimeMs
            return if (watchTime > 0) stallTimeMs.toDouble() / watchTime else null
        }
}
