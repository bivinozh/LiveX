package com.example.livex.data

/** Identifies a Google IMA DAI (Dynamic Ad Insertion) stream. */
sealed interface DaiStream {
    /** Live channel, identified by its asset key. */
    data class Live(val assetKey: String) : DaiStream

    /** VOD title: the content source (CMS) and the video id within it. */
    data class Vod(val contentSourceId: String, val videoId: String) : DaiStream
}
