package com.example.livex.player.ads

import androidx.media3.common.C
import kotlin.math.ceil

/** Where an ad break sits in the content, from AdPlaybackState's ad group time. */
enum class AdBreakKind(val label: String) {
    PRE_ROLL("pre-roll"),
    MID_ROLL("mid-roll"),
    POST_ROLL("post-roll"),
    ;

    companion object {
        /** 0 = before content; C.TIME_END_OF_SOURCE = after content; anything else = mid-roll. */
        fun fromAdGroupTimeUs(timeUs: Long): AdBreakKind = when (timeUs) {
            0L -> PRE_ROLL
            C.TIME_END_OF_SOURCE -> POST_ROLL
            else -> MID_ROLL
        }
    }
}

/** What the viewer is watching right now: content, or ad N of M in a break. */
data class AdState(
    val isPlayingAd: Boolean = false,
    val breakKind: AdBreakKind? = null,
    /** 0-based, like Player.currentAdIndexInAdGroup. */
    val adIndexInBreak: Int = 0,
    val adsInBreak: Int = 0,
    val remainingMs: Long? = null,
) {
    private val remainingSeconds: Long? get() = remainingMs?.let { ceil(it / 1000.0).toLong() }

    /** Stats overlay row, e.g. "AD · mid-roll · 2 of 3 · 8 s left"; null during content. */
    val label: String?
        get() {
            if (!isPlayingAd) return null
            val kind = breakKind?.label?.let { " · $it" } ?: ""
            val left = remainingSeconds?.let { " · $it s left" } ?: ""
            return "AD$kind · ${adIndexInBreak + 1} of $adsInBreak$left"
        }

    /** Compact form for the stats row (its label already says "Ad"), e.g. "mid-roll 2/3 · 8 s". */
    val shortLabel: String?
        get() {
            if (!isPlayingAd) return null
            val kind = breakKind?.label?.let { "$it " } ?: ""
            return "$kind${adIndexInBreak + 1}/$adsInBreak" + (remainingSeconds?.let { " · $it s" } ?: "")
        }

    /** On-video badge, e.g. "AD 2/3 · 8 s". */
    val badge: String?
        get() {
            if (!isPlayingAd) return null
            return "AD ${adIndexInBreak + 1}/$adsInBreak" + (remainingSeconds?.let { " · $it s" } ?: "")
        }
}
