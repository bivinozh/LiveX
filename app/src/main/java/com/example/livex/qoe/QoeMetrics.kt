package com.example.livex.qoe

import java.util.Locale

data class QoeMetrics(
    val playbackState: String = "Idle",
    val startupTimeMs: Long? = null,
    val bufferedDurationMs: Long = 0,
    val rebufferCount: Int = 0,
    val rebufferTimeMs: Long = 0,
    val droppedFrames: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoBitrateBps: Int? = null,
    val qualitySwitches: Int = 0,
    val bandwidthEstimateBps: Long? = null,
    val requestCount: Int = 0,
    val totalLoadTimeMs: Long = 0,
    val bytesLoaded: Long = 0,
    /** Failed segment/manifest requests. ExoPlayer retries these; many in a row end in a playback error. */
    val loadErrorCount: Int = 0,
)

enum class MetricKey(val label: String) {
    STATE("Playback state"),
    STARTUP("Startup time"),
    BUFFERED("Buffered ahead"),
    REBUFFERS("Rebuffers"),
    DROPPED("Dropped frames"),
    RESOLUTION("Resolution"),
    BITRATE("Variant bitrate"),
    SWITCHES("Quality switches"),
    BANDWIDTH("Bandwidth estimate"),
    REQUESTS("HTTP requests"),
    AVG_REQUEST_TIME("Avg request time"),
    DATA_LOADED("Data loaded"),
}

private const val NONE = "—"

fun QoeMetrics.formatted(key: MetricKey): String = when (key) {
    MetricKey.STATE -> playbackState
    MetricKey.STARTUP -> startupTimeMs?.let { "$it ms" } ?: NONE
    MetricKey.BUFFERED -> String.format(Locale.US, "%.1f s", bufferedDurationMs / 1000.0)
    MetricKey.REBUFFERS -> "$rebufferCount ($rebufferTimeMs ms)"
    MetricKey.DROPPED -> "$droppedFrames"
    MetricKey.RESOLUTION -> if (videoWidth > 0) "$videoWidth×$videoHeight" else NONE
    MetricKey.BITRATE -> videoBitrateBps?.let { "${it / 1000} kbps" } ?: NONE
    MetricKey.SWITCHES -> "$qualitySwitches"
    MetricKey.BANDWIDTH -> bandwidthEstimateBps?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) } ?: NONE
    MetricKey.REQUESTS -> "$requestCount"
    MetricKey.AVG_REQUEST_TIME -> if (requestCount > 0) "${totalLoadTimeMs / requestCount} ms" else NONE
    MetricKey.DATA_LOADED -> String.format(Locale.US, "%.1f MB", bytesLoaded / 1_000_000.0)
}
