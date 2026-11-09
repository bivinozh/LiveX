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

    /** DASH VOD. Tears of Steel, clear (no DRM). Same film as [tearsOfSteelHls]: compare HLS vs DASH. */
    val tearsOfSteelDash = Stream(
        id = "tos-dash",
        title = "Tears of Steel (DASH)",
        url = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
    )

    /**
     * HLS VOD. Tears of Steel with a subtitle rendition (EXT-X-MEDIA TYPE=SUBTITLES), hosted on the
     * same Mux CDN as [bigBuckBunny]. Replaces Apple's bipbop stream, whose CDN certificate is
     * rejected by the AOSP TV emulator ("Trust anchor for certification path not found").
     */
    val tearsOfSteelHls = Stream(
        id = "tos-hls",
        title = "Tears of Steel (HLS + subtitles)",
        url = "https://test-streams.mux.dev/tos_ismc/main.m3u8",
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
    )

    /**
     * Live HLS. Unified Streaming's 24/7 demo: multi-bitrate master, 2 s segments, sliding window
     * with EXT-X-PROGRAM-DATE-TIME. Replaces Akamai's test stream, whose master playlist now points
     * at a variant that returns HTTP 404.
     */
    val liveHls = Stream(
        id = "unified-live-hls",
        title = "Unified Streaming live",
        url = "https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8",
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

    val all: List<Stream> = listOf(bigBuckBunny, tearsOfSteelHls, tearsOfSteelDash, liveHls, dashIfLivesim)

    fun byId(id: String?): Stream? = all.firstOrNull { it.id == id }
}
