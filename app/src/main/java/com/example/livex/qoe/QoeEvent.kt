package com.example.livex.qoe

/**
 * Structured QoE events: what analytics backends (Conviva, Mux, a custom pipeline) ingest.
 *
 * Every event belongs to a session (one play request of one stream) and carries [atMs], an
 * elapsed-realtime timestamp: monotonic, so durations stay right even if the wall clock jumps.
 * The wall clock is recorded once, in [SessionStart], to place the session on a calendar.
 */
sealed interface QoeEvent {
    val sessionId: String
    val atMs: Long

    /** Short stable name, used as the event type in storage and payloads. */
    val type: String

    /** Human-readable details for the session log ("" when the type says it all). */
    val detail: String

    data class SessionStart(
        override val sessionId: String,
        override val atMs: Long,
        val streamId: String,
        val title: String,
        val isLive: Boolean,
        val startedAtEpochMs: Long,
    ) : QoeEvent {
        override val type get() = "session_start"
        override val detail get() = "$title${if (isLive) " (live)" else ""}"
    }

    /** First frame rendered. VST (video start time) = play request -> first frame. */
    data class VideoStart(
        override val sessionId: String,
        override val atMs: Long,
        val vstMs: Long,
    ) : QoeEvent {
        override val type get() = "video_start"
        override val detail get() = "VST $vstMs ms"
    }

    /** Playback stopped because the buffer ran dry (not startup, not a seek: see StallTracker). */
    data class StallStart(override val sessionId: String, override val atMs: Long) : QoeEvent {
        override val type get() = "stall_start"
        override val detail get() = ""
    }

    data class StallEnd(
        override val sessionId: String,
        override val atMs: Long,
        val durationMs: Long,
    ) : QoeEvent {
        override val type get() = "stall_end"
        override val detail get() = "$durationMs ms"
    }

    /** ABR moved to another rendition (what is decoded, not merely downloaded). */
    data class BitrateSwitch(
        override val sessionId: String,
        override val atMs: Long,
        val fromBps: Int?,
        val toBps: Int?,
        val width: Int,
        val height: Int,
    ) : QoeEvent {
        override val type get() = "bitrate_switch"
        override val detail get() = "${kbps(fromBps)} → ${kbps(toBps)} (${width}×$height)"
    }

    /** An IMA ad event (loaded, started, quartiles, completed, error...). */
    data class Ad(
        override val sessionId: String,
        override val atMs: Long,
        val name: String,
        val info: String?,
        val isError: Boolean,
    ) : QoeEvent {
        override val type get() = if (isError) "ad_error" else "ad_event"
        override val detail get() = if (info.isNullOrBlank()) name else "$name · $info"
    }

    /**
     * [isFatal] = playback stopped (onPlayerError). Non-fatal = a failed request that the
     * LoadErrorHandlingPolicy is retrying; many of those in a row are an early warning.
     */
    data class PlaybackError(
        override val sessionId: String,
        override val atMs: Long,
        val code: String,
        val message: String,
        val isFatal: Boolean,
    ) : QoeEvent {
        override val type get() = if (isFatal) "error" else "load_error"
        override val detail get() = "$code · $message"
    }

    /**
     * Exit Before Video Start: the viewer gave up before the first frame, without an error.
     * Invisible in error dashboards but often the biggest startup problem.
     */
    data class Ebvs(
        override val sessionId: String,
        override val atMs: Long,
        val waitedMs: Long,
    ) : QoeEvent {
        override val type get() = "ebvs"
        override val detail get() = "left after $waitedMs ms"
    }

    data class SessionEnd(
        override val sessionId: String,
        override val atMs: Long,
        val reason: EndReason,
        val playingTimeMs: Long,
        val stallTimeMs: Long,
        /** Time-weighted over the time actually playing; null if nothing played. */
        val avgBitrateBps: Int?,
    ) : QoeEvent {
        override val type get() = "session_end"
        override val detail get() = reason.label
    }
}

enum class EndReason(val label: String) {
    /** The viewer left the player. */
    EXIT("Exited"),

    /** Played to the end. */
    COMPLETED("Completed"),

    /** Player rebuilt (settings change): the old session ends, a new one starts. */
    RESTARTED("Restarted"),
}

private fun kbps(bps: Int?) = bps?.let { "${it / 1000} kbps" } ?: "?"
