package com.example.livex.data

import androidx.media3.common.MimeTypes

data class Stream(
    val title: String,
    val url: String,
    val mimeType: String,
    val isLive: Boolean,
) {
    /** Short label for the UI, e.g. "HLS · VOD". */
    val formatLabel: String
        get() {
            val format = when (mimeType) {
                MimeTypes.APPLICATION_M3U8 -> "HLS"
                MimeTypes.APPLICATION_MPD -> "DASH"
                else -> "Progressive"
            }
            return "$format · ${if (isLive) "Live" else "VOD"}"
        }
}

/** Public test streams (all checked to respond at the time of writing). */
object SampleStreams {

    /** Big Buck Bunny, multi-bitrate HLS ladder: good for watching ABR switches. */
    val bigBuckBunny = Stream(
        title = "Big Buck Bunny",
        url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
    )

    /** Tears of Steel as MPEG-DASH (clear, no DRM): compare DASH vs HLS behavior. */
    val tearsOfSteelDash = Stream(
        title = "Tears of Steel (DASH)",
        url = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
    )

    /** Apple's HLS reference stream: many variants, alternate audio and subtitles. */
    val bipbopAdvanced = Stream(
        title = "Apple bipbop (advanced)",
        url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
    )

    /** Akamai's 24/7 live HLS test stream: sliding live window, no fixed duration. */
    val akamaiLive = Stream(
        title = "Akamai live test",
        url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = true,
    )

    val all: List<Stream> = listOf(bigBuckBunny, tearsOfSteelDash, bipbopAdvanced, akamaiLive)
}
