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
    /**
     * VAST or VMAP ad tag for client-side ads (MediaItem.AdsConfiguration -> ImaAdsLoader).
     * Null = no ads.
     */
    val adTagUri: String? = null,
    /** Google DAI stream for server-side ads (ImaServerSideAdInsertionMediaSource). Null = no SSAI. */
    val dai: DaiStream? = null,
    /** Widevine license for protected content. Null = clear (unencrypted) stream. */
    val drm: DrmLicense? = null,
) {
    val format: String
        get() = when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> "HLS"
            MimeTypes.APPLICATION_MPD -> "DASH"
            else -> "Progressive"
        }

    /** Short label for the UI, e.g. "HLS · VOD". */
    val formatLabel: String
        get() = "$format · ${if (isLive) "Live" else "VOD"}" + when {
            adTagUri != null -> " · Ads"
            dai != null -> " · SSAI"
            drm != null -> " · Widevine"
            else -> ""
        }
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

    // --- Client-side ads (Google IMA sample tags; content = Big Buck Bunny HLS) ---

    private const val IMA_SAMPLES = "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external"

    private fun withAds(id: String, title: String, adTagUri: String) =
        bigBuckBunny.copy(id = id, title = title, adTagUri = adTagUri)

    /** VAST 3.0: one 10 s linear ad. A plain VAST tag has no schedule, so IMA plays it as a pre-roll. */
    val adsPreRoll = withAds(
        id = "bbb-ads-pre",
        title = "Big Buck Bunny + pre-roll (VAST)",
        adTagUri = "$IMA_SAMPLES/single_ad_samples&sz=640x480&cust_params=sample_ct%3Dlinear" +
            "&ciu_szs=300x250%2C728x90&gdfp_req=1&output=vast&unviewed_position_start=1&env=vp&impl=s&correlator=",
    )

    /** VMAP: ad breaks at start, 00:00:15 and end. Each break points to its own VAST. */
    val adsVmapPreMidPost = withAds(
        id = "bbb-ads-vmap",
        title = "Big Buck Bunny + pre/mid/post (VMAP)",
        adTagUri = "$IMA_SAMPLES/vmap_ad_samples&sz=640x480&cust_params=sample_ar%3Dpremidpost" +
            "&ciu_szs=300x250&gdfp_req=1&ad_rule=1&output=vmap&unviewed_position_start=1&env=vp&impl=s" +
            "&cmsid=496&vid=short_onecue&correlator=",
    )

    /** VMAP with a single break at "end": plays after the content (use 60 s ⏩ to get there). */
    val adsPostRoll = withAds(
        id = "bbb-ads-post",
        title = "Big Buck Bunny + post-roll (VMAP)",
        adTagUri = "$IMA_SAMPLES/vmap_ad_samples&sz=640x480&cust_params=sample_ar%3Dpostonly" +
            "&ciu_szs=300x250&gdfp_req=1&ad_rule=1&output=vmap&unviewed_position_start=1&env=vp&impl=s&correlator=",
    )

    /** VAST Wrapper that redirects to an invalid tag: the ad fails, content must still play. */
    val adsError = withAds(
        id = "bbb-ads-error",
        title = "Big Buck Bunny + broken ad (VAST error)",
        adTagUri = "$IMA_SAMPLES/single_ad_samples&sz=640x480&cust_params=sample_ct%3Dredirecterror" +
            "&ciu_szs=300x250%2C728x90&gdfp_req=1&output=vast&unviewed_position_start=1&env=vp&impl=s&nofb=1&correlator=",
    )

    // --- Server-side ads (Google IMA DAI public samples, as used by the Media3 demo app) ---
    // The stream URL is created by the DAI service per session (with ads stitched in), so [url] is
    // only a description here; the engine builds the imadai URI from [Stream.dai].

    private const val DAI = "https://dai.google.com"

    /** HLS VOD with pre-, mid- and post-roll breaks stitched in by DAI. */
    val daiVodHls = Stream(
        id = "dai-vod-hls",
        title = "Tears of Steel (DAI VOD)",
        url = DAI,
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = false,
        dai = DaiStream.Vod(contentSourceId = "2528370", videoId = "tears-of-steel"),
    )

    /** HLS live channel with DAI mid-roll breaks (triggered by SCTE-35 in the source). */
    val daiLiveHls = Stream(
        id = "dai-live-hls",
        title = "Big Buck Bunny (DAI live)",
        url = DAI,
        mimeType = MimeTypes.APPLICATION_M3U8,
        isLive = true,
        dai = DaiStream.Live(assetKey = "sN_IYUG8STe1ZzhIIE_ksA"),
    )

    /** DASH VOD where every ad is its own Period (multi-period MPD). */
    val daiVodDash = Stream(
        id = "dai-vod-dash",
        title = "Tears of Steel (DAI VOD, multi-period DASH)",
        url = DAI,
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
        dai = DaiStream.Vod(contentSourceId = "2559737", videoId = "tos-dash"),
    )

    // --- Widevine DRM (Google's test content + UAT license proxy, from the Media3 demo list) ---
    // video_id picks the license POLICY: 2015_tears allows software (L3) devices,
    // GTS_HW_SECURE_ALL only issues keys for hardware-secure (L1) decoding.

    private const val WV_TEARS = "https://storage.googleapis.com/wvmedia/cenc/h264/tears"
    private const val WV_PROXY = "https://proxy.uat.widevine.com/proxy?provider=widevine_test&video_id="

    /** Widevine SD (up to 380p): plays on L3. */
    val widevineSd = Stream(
        id = "wv-sd",
        title = "Tears of Steel (Widevine SD)",
        url = "$WV_TEARS/tears_sd.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
        drm = DrmLicense("${WV_PROXY}2015_tears"),
    )

    /** Widevine up to 1920×856: whether HD keys are released depends on the device level. */
    val widevineHd = Stream(
        id = "wv-hd",
        title = "Tears of Steel (Widevine HD)",
        url = "$WV_TEARS/tears.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
        drm = DrmLicense("${WV_PROXY}2015_tears"),
    )

    /** Policy requires hardware-secure decoding: expected to FAIL on L3 (shows the DRM error path). */
    val widevineL1Only = Stream(
        id = "wv-l1-only",
        title = "Tears of Steel (Widevine, L1 required)",
        url = "$WV_TEARS/tears.mpd",
        mimeType = MimeTypes.APPLICATION_MPD,
        isLive = false,
        drm = DrmLicense("${WV_PROXY}GTS_HW_SECURE_ALL"),
    )

    val all: List<Stream> = listOf(
        bigBuckBunny, tearsOfSteelHls, tearsOfSteelDash, liveHls, dashIfLivesim,
        adsPreRoll, adsVmapPreMidPost, adsPostRoll, adsError,
        daiVodHls, daiLiveHls, daiVodDash,
        widevineSd, widevineHd, widevineL1Only,
    )

    fun byId(id: String?): Stream? = all.firstOrNull { it.id == id }
}
