package com.example.livex.player.ads

/**
 * One ad break on the content timeline.
 * @param timeMs content position of the break; [POST_ROLL] for "after the content".
 */
data class AdBreak(val timeMs: Long, val isPlayed: Boolean) {
    val isPostRoll: Boolean get() = timeMs == POST_ROLL

    companion object {
        const val POST_ROLL = Long.MAX_VALUE
    }
}

sealed interface SeekOutcome {
    data class Seeked(val toMs: Long) : SeekOutcome

    /** The seek jumped over an unwatched break: play it from [adTimeMs], then continue at [resumeAtMs]. */
    data class SnappedToAd(val adTimeMs: Long, val resumeAtMs: Long) : SeekOutcome

    /** Ads aren't seekable. */
    data object BlockedDuringAd : SeekOutcome
}

/**
 * "Snap-back": viewers can't skip paid ad breaks by seeking over them.
 *
 * - Forward over unwatched breaks: play the LAST one skipped (playing every skipped break would
 *   punish scrubbing; one is the common industry rule), then resume at the requested position.
 * - Backward: free (the breaks behind you were either watched or are before where you started).
 * - Post-roll: never snaps; it plays when the content reaches the end.
 * Pure Kotlin: the engine supplies positions and breaks from the player's AdPlaybackState.
 */
object AdSeekPolicy {
    fun resolve(fromMs: Long, toMs: Long, breaks: List<AdBreak>): SeekOutcome {
        if (toMs <= fromMs) return SeekOutcome.Seeked(toMs)
        val lastSkipped = breaks
            .filter { !it.isPlayed && !it.isPostRoll && it.timeMs > fromMs && it.timeMs <= toMs }
            .maxByOrNull { it.timeMs }
            ?: return SeekOutcome.Seeked(toMs)
        return SeekOutcome.SnappedToAd(adTimeMs = lastSkipped.timeMs, resumeAtMs = toMs)
    }
}
