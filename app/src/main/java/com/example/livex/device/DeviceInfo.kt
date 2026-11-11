package com.example.livex.device

import com.example.livex.player.drm.WidevineInfo
import java.util.Locale

/** Video codecs a streaming service cares about, in rough order of age (and efficiency). */
enum class VideoCodec(val label: String, val mime: String) {
    AVC("H.264 / AVC", "video/avc"),
    HEVC("H.265 / HEVC", "video/hevc"),
    VP9("VP9", "video/x-vnd.on2.vp9"),
    AV1("AV1", "video/av01"),
    DOLBY_VISION("Dolby Vision", "video/dolby-vision"),
    ;

    companion object {
        fun fromMime(mime: String): VideoCodec? = entries.firstOrNull { it.mime.equals(mime, ignoreCase = true) }
    }
}

enum class HdrFormat(val label: String) {
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    HLG("HLG"),
    DOLBY_VISION("Dolby Vision"),
}

/** One decoder from MediaCodecList, already reduced to what matters for streaming. */
data class DecoderEntry(
    val codec: VideoCodec,
    val name: String,
    /** Runs on dedicated silicon. Software decoders burn CPU and rarely manage 4K. */
    val isHardware: Boolean,
    /** Decodes 3840×2160 at 30 fps (VideoCapabilities.areSizeAndRateSupported). */
    val supports4k: Boolean,
    val hdrFormats: Set<HdrFormat> = emptySet(),
    /** FEATURE_TunneledPlayback: decoder renders straight to the display, A/V synced in hardware. */
    val isTunneled: Boolean = false,
    /** FEATURE_SecurePlayback: decodes into protected memory (needed for Widevine L1 HD/UHD). */
    val isSecure: Boolean = false,
)

/** Everything known about one codec on this device: the best decoder plus features any decoder has. */
data class CodecSupport(
    val codec: VideoCodec,
    val isHardware: Boolean,
    val supports4k: Boolean,
    val hdrFormats: Set<HdrFormat>,
    val isTunneled: Boolean,
    val isSecure: Boolean,
    val decoderNames: List<String>,
) {
    /** "HW · 4K · HDR10, HLG · tunneled · secure" */
    val featureText: String
        get() = buildList {
            add(if (isHardware) "HW" else "software only")
            if (supports4k) add("4K")
            if (hdrFormats.isNotEmpty()) add(hdrFormats.sortedBy { it.ordinal }.joinToString { it.label })
            if (isTunneled) add("tunneled")
            if (isSecure) add("secure")
        }.joinToString(" · ")

    companion object {
        /**
         * Merges a codec's decoders. Hardware decoders win: if one exists, software ones are ignored
         * (ExoPlayer prefers hardware too). Features are OR-ed across the remaining decoders, since
         * e.g. the ".secure" variant of a decoder is a separate MediaCodecList entry.
         */
        fun merge(entries: List<DecoderEntry>): List<CodecSupport> =
            entries.groupBy { it.codec }
                .map { (codec, all) ->
                    val usable = all.filter { it.isHardware }.ifEmpty { all }
                    CodecSupport(
                        codec = codec,
                        isHardware = usable.any { it.isHardware },
                        supports4k = usable.any { it.supports4k },
                        hdrFormats = usable.flatMap { it.hdrFormats }.toSet(),
                        isTunneled = usable.any { it.isTunneled },
                        isSecure = usable.any { it.isSecure },
                        decoderNames = usable.map { it.name },
                    )
                }
                .sortedBy { it.codec.ordinal }
    }
}

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val refreshRateHz: Float,
    /** Largest mode the display supports (a TV may run its UI at 1080p but offer 4K modes). */
    val maxWidthPx: Int,
    val maxHeightPx: Int,
    val hdrFormats: Set<HdrFormat>,
)

data class MemoryInfo(
    val totalRamMb: Long,
    /** Per-app heap limit (ActivityManager.memoryClass). Bitmaps and buffers count against it. */
    val memoryClassMb: Int,
    /** ActivityManager.isLowRamDevice: the system itself is told to save memory (common on cheap TV sticks). */
    val isLowRam: Boolean,
)

data class InfoRow(val label: String, val value: String, val isGood: Boolean? = null)

data class DeviceInfo(
    val model: String,
    val sdkInt: Int,
    val isTelevision: Boolean,
    val codecs: List<CodecSupport>,
    val display: DisplayInfo,
    val memory: MemoryInfo,
    val widevine: WidevineInfo?,
) {
    private fun hw(codec: VideoCodec) = codecs.firstOrNull { it.codec == codec && it.isHardware }

    /** A hardware decoder can do 4K... */
    val canDecode4k: Boolean get() = codecs.any { it.isHardware && it.supports4k }

    /** ...and the screen can show it. Both are needed before offering 4K renditions. */
    val canPlay4k: Boolean get() = canDecode4k && display.maxWidthPx >= UHD_WIDTH && display.maxHeightPx >= UHD_HEIGHT

    val hasHardwareHevc: Boolean get() = hw(VideoCodec.HEVC) != null

    /** HDR the decoders can output AND the display can show. */
    val playableHdr: Set<HdrFormat>
        get() = codecs.filter { it.isHardware }.flatMap { it.hdrFormats }.toSet() intersect display.hdrFormats

    val supportsTunneling: Boolean get() = codecs.any { it.isHardware && it.isTunneled }

    /** The answers a streaming app needs before it builds its track-selection constraints. */
    fun summaryRows(): List<InfoRow> = listOf(
        InfoRow(
            "4K (2160p)",
            when {
                canPlay4k -> "Yes"
                canDecode4k -> "Decoder yes, display no (${display.maxWidthPx}×${display.maxHeightPx})"
                else -> "No"
            },
            canPlay4k,
        ),
        InfoRow("HEVC hardware decoding", if (hasHardwareHevc) "Yes" else "No", hasHardwareHevc),
        InfoRow(
            "HDR",
            when {
                playableHdr.isNotEmpty() -> playableHdr.sortedBy { it.ordinal }.joinToString { it.label }
                // Hardware only, like 4K and HEVC: a software 10-bit decoder won't keep up with HDR video.
                codecs.any { it.isHardware && it.hdrFormats.isNotEmpty() } -> "Decoder yes, display no"
                else -> "No"
            },
            playableHdr.isNotEmpty(),
        ),
        InfoRow("Tunneled playback", if (supportsTunneling) "Supported" else "Not supported", supportsTunneling),
        InfoRow(
            "Widevine",
            widevine?.label ?: "Checking…",
            (widevine as? WidevineInfo.Available)?.isHardwareSecure,
        ),
    )

    fun displayRows(): List<InfoRow> = listOf(
        InfoRow(
            "Current mode",
            String.format(Locale.US, "%d×%d @ %.0f Hz", display.widthPx, display.heightPx, display.refreshRateHz),
        ),
        InfoRow("Largest mode", "${display.maxWidthPx}×${display.maxHeightPx}"),
        InfoRow(
            "Display HDR",
            display.hdrFormats.sortedBy { it.ordinal }.joinToString { it.label }.ifEmpty { "None" },
        ),
    )

    fun memoryRows(): List<InfoRow> = listOf(
        InfoRow("Total RAM", String.format(Locale.US, "%.1f GB", memory.totalRamMb / 1024.0)),
        InfoRow("App heap limit", "${memory.memoryClassMb} MB"),
        InfoRow("Low-RAM device", if (memory.isLowRam) "Yes: use small buffers" else "No", !memory.isLowRam),
    )

    companion object {
        const val UHD_WIDTH = 3840
        const val UHD_HEIGHT = 2160
    }
}
