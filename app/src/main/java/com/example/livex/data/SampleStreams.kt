package com.example.livex.data

import androidx.media3.common.MimeTypes

data class Stream(
    /** Stable id, used as the navigation argument (indexes would break if the list changes). */
    val id: String,
    val title: String,
    val url: String,
    /** MimeTypes.APPLICATION_M3U8 (HLS) or MimeTypes.APPLICATION_MPD (DASH). */
    val mimeType: String,
    val isLive: Boolean,
) {
    val format: String
        get() = when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> "HLS"
            MimeTypes.APPLICATION_MPD -> "DASH"
            else -> "Progressive"
        }

    /** Short label for the UI, e.g. "HLS · VOD". */
    val formatLabel: String
        get() = "$format · ${if (isLive) "Live" else "VOD"}"
}

/** Public test streams (all checked to respond at the time of writing). */
object SampleStreams {

    /** HLS VOD. Big Buck Bunny with a multi-bitrate ladder: good for watching ABR switches. */
    val bigBuckBunny = Stream(
        id = "bbb-hls",
        title = "Big Buck Bunny",
        url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
    )

    /** DASH VOD. Tears of Steel, clear (no DRM). */
    val tearsOfSteelDash = Stream(
        id = "tos-dash",
        title = "Tears of Steel",
        url = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
    )

    /** HLS VOD. Apple's reference stream: many variants, alternate audio and subtitles. */
    val bipbopAdvanced = Stream(
        id = "bipbop-hls",
        title = "Apple bipbop (advanced)",
        url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
    )

    /** Live HLS. Akamai's 24/7 test stream with a sliding live window. */
    val akamaiLive = Stream(
        id = "akamai-live-hls",
        title = "Akamai live test",
        url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = true,
    )

    /** Live DASH. DASH-IF livesim2: a simulated live MPD (type="dynamic") with 2 s segments. */
    val dashIfLivesim = Stream(
        id = "dashif-livesim2",
        title = "DASH-IF livesim2 (test pattern)",
        url = "https://livesim2.dashif.org/livesim2/testpic_2s/Manifest.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = true,
    )

    val all: List<Stream> = listOf(bigBuckBunny, tearsOfSteelDash, bipbopAdvanced, akamaiLive, dashIfLivesim)

    fun byId(id: String?): Stream? = all.firstOrNull { it.id == id }
}
