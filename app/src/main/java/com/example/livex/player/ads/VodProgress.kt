package com.example.livex.player.ads

/**
 * VOD progress for the seek bar, in CONTENT time (ads excluded, like Player.contentPosition), with
 * the ad breaks from the AdPlaybackState (client-side VMAP or server-side DAI).
 */
data class VodProgress(
    val positionMs: Long,
    val durationMs: Long,
    val adBreaks: List<AdBreak>,
) {
    val positionFraction: Float get() = fraction(positionMs)

    fun fractionOf(adBreak: AdBreak): Float = if (adBreak.isPostRoll) 1f else fraction(adBreak.timeMs)

    val timeLabel: String
        get() = "${clock(positionMs)} / ${if (durationMs > 0) clock(durationMs) else "--:--"}"

    val adSummary: String
        get() = if (adBreaks.isEmpty()) {
            "No ad breaks"
        } else {
            "${adBreaks.size} ad break${if (adBreaks.size == 1) "" else "s"} · ${adBreaks.count { it.isPlayed }} watched"
        }

    private fun fraction(ms: Long): Float =
        if (durationMs <= 0) 0f else (ms.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        fun clock(ms: Long): String {
            val totalSeconds = ms / 1000
            return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
    }
}
