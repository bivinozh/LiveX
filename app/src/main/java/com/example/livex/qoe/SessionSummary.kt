package com.example.livex.qoe

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One row of the Sessions screen: a session's events folded into the numbers a QoE dashboard shows.
 * Pure Kotlin; [applyEvent] is a reducer, so the summary can be rebuilt from the stored event log.
 */
data class SessionSummary(
    val sessionId: String,
    val streamId: String,
    val title: String,
    val isLive: Boolean,
    val startedAtEpochMs: Long,
    /** Elapsed-realtime of session_start; event offsets are relative to it. */
    val startAtMs: Long,
    val durationMs: Long = 0,
    val vstMs: Long? = null,
    val isEbvs: Boolean = false,
    val ebvsWaitMs: Long? = null,
    val stallCount: Int = 0,
    val stallTimeMs: Long = 0,
    /** Known once the session ends. */
    val playingTimeMs: Long? = null,
    val avgBitrateBps: Int? = null,
    val bitrateSwitches: Int = 0,
    /** Fatal playback errors (retried request failures don't count). */
    val failures: Int = 0,
    val lastError: String? = null,
    /** Null while the session is open (or if the app died before it ended). */
    val endReason: EndReason? = null,
) {
    /** Stall time / (playing + stall time); null until the session ended (playing time unknown). */
    val rebufferingRatio: Double?
        get() {
            val playing = playingTimeMs ?: return null
            val watch = playing + stallTimeMs
            return if (watch > 0) stallTimeMs.toDouble() / watch else null
        }

    fun applyEvent(event: QoeEvent): SessionSummary {
        if (event.sessionId != sessionId) return this
        val updated = copy(durationMs = (event.atMs - startAtMs).coerceAtLeast(durationMs))
        return when (event) {
            is QoeEvent.SessionStart -> updated
            is QoeEvent.VideoStart -> updated.copy(vstMs = event.vstMs)
            is QoeEvent.StallStart -> updated.copy(stallCount = stallCount + 1)
            is QoeEvent.StallEnd -> updated.copy(stallTimeMs = stallTimeMs + event.durationMs)
            is QoeEvent.BitrateSwitch -> updated.copy(bitrateSwitches = bitrateSwitches + 1)
            is QoeEvent.Ad -> updated
            is QoeEvent.PlaybackError ->
                if (event.isFatal) updated.copy(failures = failures + 1, lastError = event.message) else updated
            is QoeEvent.Ebvs -> updated.copy(isEbvs = true, ebvsWaitMs = event.waitedMs)
            is QoeEvent.SessionEnd -> updated.copy(
                endReason = event.reason,
                playingTimeMs = event.playingTimeMs,
                // The tracker's total is exact (StallEnd sums can miss a stall cut by a rebuild).
                stallTimeMs = event.stallTimeMs,
                avgBitrateBps = event.avgBitrateBps,
            )
        }
    }

    /** "Nov 14, 22:13 · 1:00 · Completed" */
    fun headerText(zone: ZoneId = ZoneId.systemDefault()): String {
        val date = DATE_FORMAT.withZone(zone).format(Instant.ofEpochMilli(startedAtEpochMs))
        val totalS = durationMs / 1000
        val length = String.format(Locale.US, "%d:%02d", totalS / 60, totalS % 60)
        return "$date · $length · ${endReason?.label ?: "In progress"}"
    }

    /** "VST 900 ms · Rebuffering 1.7 % · Stalls 1 · Avg 2.2 Mbps · Switches 0 · Failures 0" */
    val metricsText: String
        get() {
            if (isEbvs) {
                val waited = String.format(Locale.US, "%.1f s", (ebvsWaitMs ?: 0) / 1000.0)
                return "Left before video start (EBVS) after $waited · Failures $failures"
            }
            val vst = vstMs?.let { "$it ms" } ?: NONE
            val ratio = rebufferingRatio?.let { String.format(Locale.US, "%.1f %%", it * 100) } ?: NONE
            val bitrate = avgBitrateBps?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) } ?: NONE
            return "VST $vst · Rebuffering $ratio · Stalls $stallCount · Avg $bitrate · " +
                "Switches $bitrateSwitches · Failures $failures"
        }

    companion object {
        private const val NONE = "—"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

        fun start(event: QoeEvent.SessionStart) = SessionSummary(
            sessionId = event.sessionId,
            streamId = event.streamId,
            title = event.title,
            isLive = event.isLive,
            startedAtEpochMs = event.startedAtEpochMs,
            startAtMs = event.atMs,
        )
    }
}
