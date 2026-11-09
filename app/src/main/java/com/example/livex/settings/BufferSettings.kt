package com.example.livex.settings

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import java.util.Locale

/**
 * The four durations passed to DefaultLoadControl.Builder.setBufferDurationsMs.
 *
 * Water-tank analogy: the buffer is a tank between the network (the tap filling it) and the
 * decoder (the drain emptying it at exactly 1 s of media per second).
 */
data class BufferSettings(
    /** Low-water mark: below this the tap is always open. */
    val minBufferMs: Int,
    /** High-water mark: at this level the tap closes. */
    val maxBufferMs: Int,
    /** Water needed before the drain opens for the very first time (startup). */
    val bufferForPlaybackMs: Int,
    /** Water needed before the drain reopens after the tank ran dry (a stall). */
    val bufferForPlaybackAfterRebufferMs: Int,
) {
    /** Returns a copy with [field] set to [valueMs], adjusting the others so the settings stay valid. */
    fun with(field: BufferField, valueMs: Int): BufferSettings {
        val v = valueMs.coerceIn(field.range)
        return when (field) {
            BufferField.MIN_BUFFER -> copy(
                minBufferMs = v,
                maxBufferMs = maxOf(maxBufferMs, v),
                bufferForPlaybackMs = minOf(bufferForPlaybackMs, v),
                bufferForPlaybackAfterRebufferMs = minOf(bufferForPlaybackAfterRebufferMs, v),
            )
            BufferField.MAX_BUFFER -> copy(maxBufferMs = v).with(BufferField.MIN_BUFFER, minOf(minBufferMs, v))
                .copy(maxBufferMs = v)
            BufferField.FOR_PLAYBACK -> copy(
                bufferForPlaybackMs = v,
                minBufferMs = maxOf(minBufferMs, v),
                maxBufferMs = maxOf(maxBufferMs, v),
            )
            BufferField.PLAYBACK_AFTER_REBUFFER -> copy(
                bufferForPlaybackAfterRebufferMs = v,
                minBufferMs = maxOf(minBufferMs, v),
                maxBufferMs = maxOf(maxBufferMs, v),
            )
        }
    }

    /**
     * Enforces what DefaultLoadControl requires (it throws IllegalArgumentException otherwise):
     * thresholds >= 0, min >= both thresholds, max >= min. Used on values read from storage.
     */
    fun normalized(): BufferSettings {
        val forPlayback = bufferForPlaybackMs.coerceIn(BufferField.FOR_PLAYBACK.range)
        val afterRebuffer = bufferForPlaybackAfterRebufferMs.coerceIn(BufferField.PLAYBACK_AFTER_REBUFFER.range)
        val min = maxOf(minBufferMs, forPlayback, afterRebuffer).coerceIn(BufferField.MIN_BUFFER.range)
        val max = maxOf(maxBufferMs, min).coerceIn(BufferField.MAX_BUFFER.range)
        return BufferSettings(min, max, forPlayback, afterRebuffer)
    }

    /** One line for the guide, e.g. "start after 500 ms, keep 15–30 s buffered". */
    fun summary(): String =
        "start after $bufferForPlaybackMs ms, keep ${minBufferMs / 1000}–${maxBufferMs / 1000} s buffered"

    companion object {
        /** Media3's own defaults, read from the library (50 s / 50 s / 1 s / 2 s in Media3 1.x). */
        val MEDIA3_DEFAULT: BufferSettings = media3Defaults()

        /** Used until the user saves something: the "Fast start" preset. */
        val DEFAULT: BufferSettings get() = BufferPreset.FAST_START.settings
    }
}

@OptIn(UnstableApi::class)
private fun media3Defaults() = BufferSettings(
    minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
    maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
    bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
)

/** The four sliders: range, D-pad step and the explanations shown on the settings screen. */
enum class BufferField(
    val label: String,
    val range: IntRange,
    val step: Int,
    /** Water-tank analogy. */
    val analogy: String,
    /** What turning it up/down costs. */
    val tradeOff: String,
) {
    MIN_BUFFER(
        label = "minBufferMs",
        range = 1_000..120_000,
        step = 1_000,
        analogy = "Low-water mark. Whenever the tank is below this level, the tap is always open: " +
            "the player keeps downloading no matter what.",
        tradeOff = "Higher: more protection against network dips, but more data and memory. " +
            "Lower: lighter, but a short outage can drain the tank and stall playback.",
    ),
    MAX_BUFFER(
        label = "maxBufferMs",
        range = 1_000..180_000,
        step = 5_000,
        analogy = "High-water mark. When the tank reaches this level the tap closes; it reopens once " +
            "the level falls back below minBufferMs. The gap between the two decides how often the tap cycles.",
        tradeOff = "Higher: rides out long outages, but uses more memory and wastes data if the viewer quits " +
            "early. Equal to min (Media3's default): the tap trickles to keep the level constant.",
    ),
    FOR_PLAYBACK(
        label = "bufferForPlaybackMs",
        range = 0..10_000,
        step = 250,
        analogy = "Water needed before the drain opens for the very first time, at startup or after a seek.",
        tradeOff = "Lower: faster startup (VST). Too low: the first seconds play from an almost empty tank, " +
            "so an early stall is more likely.",
    ),
    PLAYBACK_AFTER_REBUFFER(
        label = "bufferForPlaybackAfterRebufferMs",
        range = 0..15_000,
        step = 250,
        analogy = "Water needed before the drain reopens after the tank ran dry (a stall). Usually higher " +
            "than bufferForPlaybackMs: running dry proved the tap is slower than the drain.",
        tradeOff = "Lower: shorter stalls, but more of them (stall, play a moment, stall again). " +
            "Higher: fewer, longer stalls. Viewers dislike repeated stalls more than one longer wait.",
    ),
    ;

    fun get(settings: BufferSettings): Int = when (this) {
        MIN_BUFFER -> settings.minBufferMs
        MAX_BUFFER -> settings.maxBufferMs
        FOR_PLAYBACK -> settings.bufferForPlaybackMs
        PLAYBACK_AFTER_REBUFFER -> settings.bufferForPlaybackAfterRebufferMs
    }

    fun format(valueMs: Int): String =
        if (valueMs < 1_000) "$valueMs ms" else String.format(Locale.US, "%.1f s", valueMs / 1000.0)
}

/** Ready-made settings for common situations. */
enum class BufferPreset(val label: String, val settings: BufferSettings, val description: String) {
    FAST_START(
        label = "Fast start",
        settings = BufferSettings(15_000, 30_000, 500, 1_500),
        description = "Opens the drain at 0.5 s: lowest startup time, moderate tank.",
    ),
    STABLE_VOD(
        label = "Stable (VOD)",
        settings = BufferSettings(30_000, 120_000, 2_500, 5_000),
        description = "A big tank (up to 2 min) rides out long outages; slower start, more memory and data.",
    ),
    LOW_LATENCY_LIVE(
        label = "Low latency (live)",
        settings = BufferSettings(2_000, 6_000, 500, 1_000),
        description = "Live content doesn't exist yet beyond the live edge, so a big tank is impossible anyway: " +
            "keep it small to stay close to live.",
    ),
    LOW_MEMORY_TV(
        label = "Low memory (TV)",
        settings = BufferSettings(8_000, 15_000, 1_000, 2_000),
        description = "Small tank for low-RAM TV boxes and sticks: avoids OOM kills, at the cost of resilience.",
    ),
    ;

    companion object {
        fun matching(settings: BufferSettings): BufferPreset? = entries.firstOrNull { it.settings == settings }
    }
}
