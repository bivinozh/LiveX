package com.example.livex.data

import com.example.livex.player.AbrProfile
import com.example.livex.player.BufferProfile
import com.example.livex.player.OptimizationToggle
import com.example.livex.qoe.MetricKey

/** A Default-vs-Optimized demo the viewer can switch live. */
data class Optimization(
    val toggle: OptimizationToggle,
    val defaultLabel: String,
    val optimizedLabel: String,
    val tradeOff: String,
)

/** One learning topic shown in the guide overlay. */
data class Topic(
    val title: String,
    val description: String,
    val guidance: List<String>,
    val metrics: List<MetricKey>,
    val optimization: Optimization? = null,
)

object Topics {

    private fun BufferProfile.summary() =
        "start after ${bufferForPlaybackMs} ms, keep ${minBufferMs / 1000}–${maxBufferMs / 1000} s buffered"

    val hlsBasics = Topic(
        title = "HLS & DASH playback basics",
        description = "ExoPlayer downloads the HLS playlist or DASH manifest, picks a quality, " +
            "then fetches short media segments into a buffer and plays them.",
        guidance = listOf(
            "MediaItem describes WHAT to play: the URL plus the MIME type (M3U8 = HLS, MPD = DASH).",
            "DefaultMediaSourceFactory uses that MIME type to choose HlsMediaSource or DashMediaSource.",
            "Live streams (HLS live, DASH dynamic MPD) join at the live edge and keep refreshing the manifest.",
            "setMediaItem → prepare → playWhenReady = true starts loading and playback.",
            "The player lives in the ViewModel, so it survives configuration changes.",
            "release() in onCleared frees decoders, memory and network connections.",
        ),
        metrics = listOf(MetricKey.STATE, MetricKey.RESOLUTION, MetricKey.BUFFERED),
    )

    val bufferTuning = Topic(
        title = "Startup & buffer tuning",
        description = "DefaultLoadControl decides how much media must be buffered before playback " +
            "starts, and how much to keep buffered while playing.",
        guidance = listOf(
            "bufferForPlaybackMs: media needed before the first frame. Lower = faster start.",
            "minBufferMs / maxBufferMs: the buffer target. Lower = less memory and data.",
            "bufferForPlaybackAfterRebufferMs: media needed to resume after a stall.",
            "Switch the optimization and compare Startup time and Buffered ahead.",
        ),
        metrics = listOf(MetricKey.STARTUP, MetricKey.BUFFERED, MetricKey.REBUFFERS),
        optimization = Optimization(
            toggle = OptimizationToggle.BUFFER,
            defaultLabel = "Media3 default: ${BufferProfile.Default.summary()}",
            optimizedLabel = "Tuned: ${BufferProfile.Tuned.summary()}",
            tradeOff = "A smaller buffer starts faster and uses less memory, but gives less " +
                "protection against network dips, so rebuffers become more likely.",
        ),
    )

    val abr = Topic(
        title = "Adaptive bitrate (ABR)",
        description = "The track selector picks a variant from the HLS bitrate ladder using the " +
            "bandwidth estimate, and switches up or down as the network changes.",
        guidance = listOf(
            "Watch Resolution and Variant bitrate climb as the bandwidth estimate builds up.",
            "TrackSelectionParameters set limits on which variants ABR may choose.",
            "Capping quality to what the screen or user needs saves data and CDN cost.",
        ),
        metrics = listOf(
            MetricKey.RESOLUTION, MetricKey.BITRATE, MetricKey.SWITCHES,
            MetricKey.BANDWIDTH, MetricKey.DATA_LOADED,
        ),
        optimization = Optimization(
            toggle = OptimizationToggle.ABR,
            defaultLabel = "Default: any variant in the ladder",
            optimizedLabel = "Data saver: max ${AbrProfile.DATA_SAVER_MAX_WIDTH}×${AbrProfile.DATA_SAVER_MAX_HEIGHT}",
            tradeOff = "Lower peak picture quality in exchange for less data downloaded.",
        ),
    )

    val network = Topic(
        title = "Network: OkHttp data source",
        description = "Every playlist, manifest and segment is an HTTP request. The data source " +
            "controls connections, caching and request headers.",
        guidance = listOf(
            "ON (default): OkHttpDataSource with one shared OkHttpClient: connection pooling, " +
                "HTTP/2 and a 50 MB disk cache that outlive player restarts.",
            "OFF: DefaultHttpDataSource (the platform HttpURLConnection), for comparison.",
            "Both send a custom User-Agent (LiveX/<version> … AndroidXMedia3/<version>) so CDN logs show the app.",
            "Compare Avg request time; re-fetched segments may come from the disk cache if the CDN allows caching.",
            "OkHttp interceptors are also where auth tokens, headers and logging go.",
        ),
        metrics = listOf(
            MetricKey.REQUESTS, MetricKey.AVG_REQUEST_TIME, MetricKey.DATA_LOADED, MetricKey.BANDWIDTH,
        ),
        optimization = Optimization(
            toggle = OptimizationToggle.NETWORK,
            defaultLabel = "Platform: DefaultHttpDataSource",
            optimizedLabel = "OkHttpDataSource (shared client + cache)",
            tradeOff = "Adds the OkHttp dependency; gains depend on the CDN and network.",
        ),
    )

    val qoe = Topic(
        title = "QoE metrics",
        description = "Quality of Experience numbers describe what the viewer actually experiences. " +
            "Use them to judge every optimization.",
        guidance = listOf(
            "Startup time: from prepare() to the first rendered frame.",
            "Rebuffers: stalls after playback has started (count and total time).",
            "Dropped frames: the decoder or renderer couldn't keep up.",
            "Change one optimization at a time and compare the numbers.",
        ),
        metrics = MetricKey.entries,
    )

    val all: List<Topic> = listOf(hlsBasics, bufferTuning, abr, network, qoe)
}
