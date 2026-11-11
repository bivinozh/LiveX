package com.example.livex.device

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.example.livex.playback.DeviceCapabilities
import com.example.livex.player.drm.WidevineInfoReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface DeviceInfoReader {
    /** Blocking (MediaCodecList, MediaDrm): call off the main thread. */
    fun read(): DeviceInfo
}

/**
 * Asks the platform what it can actually decode and show:
 * - MediaCodecList: every decoder, with its size/frame-rate limits, profiles (HDR) and features
 *   (tunneled, secure). This is also what ExoPlayer's MediaCodecSelector consults per track.
 * - DisplayManager: display modes and HDR types (decoding HDR is useless if the panel can't show it).
 * - ActivityManager: RAM and the per-app heap limit.
 */
class AndroidDeviceInfoReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val capabilities: DeviceCapabilities,
) : DeviceInfoReader {

    override fun read(): DeviceInfo = DeviceInfo(
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        sdkInt = Build.VERSION.SDK_INT,
        isTelevision = capabilities.isTelevision,
        codecs = CodecSupport.merge(decoders()),
        display = display(),
        memory = memory(),
        widevine = WidevineInfoReader.read(),
    )

    private fun decoders(): List<DecoderEntry> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            // Aliases are other names for the same decoder: counting them would duplicate entries.
            .filter { !it.isEncoder && !it.isAlias }
            .flatMap { info ->
                info.supportedTypes.mapNotNull { mime ->
                    val codec = VideoCodec.fromMime(mime) ?: return@mapNotNull null
                    val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: return@mapNotNull null
                    entry(info, codec, caps)
                }
            }

    private fun entry(info: MediaCodecInfo, codec: VideoCodec, caps: CodecCapabilities): DecoderEntry {
        val video = caps.videoCapabilities
        return DecoderEntry(
            codec = codec,
            name = info.name,
            isHardware = info.isHardwareAccelerated,
            supports4k = video?.areSizeAndRateSupported(DeviceInfo.UHD_WIDTH, DeviceInfo.UHD_HEIGHT, 30.0) == true,
            hdrFormats = hdrFormats(codec, caps.profileLevels.map { it.profile }.toSet()),
            isTunneled = caps.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback),
            isSecure = caps.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback),
        )
    }

    /** HDR shows up as codec PROFILES: 10-bit + HDR metadata variants. */
    private fun hdrFormats(codec: VideoCodec, profiles: Set<Int>): Set<HdrFormat> = buildSet {
        when (codec) {
            VideoCodec.HEVC -> {
                if (CodecProfileLevel.HEVCProfileMain10HDR10 in profiles) add(HdrFormat.HDR10)
                if (CodecProfileLevel.HEVCProfileMain10HDR10Plus in profiles) add(HdrFormat.HDR10_PLUS)
                // HLG is plain 10-bit with a different transfer curve: no metadata profile needed.
                if (CodecProfileLevel.HEVCProfileMain10 in profiles) add(HdrFormat.HLG)
            }
            VideoCodec.VP9 -> {
                if (CodecProfileLevel.VP9Profile2HDR in profiles || CodecProfileLevel.VP9Profile3HDR in profiles) {
                    add(HdrFormat.HDR10)
                }
                if (CodecProfileLevel.VP9Profile2HDR10Plus in profiles) add(HdrFormat.HDR10_PLUS)
            }
            VideoCodec.AV1 -> {
                if (CodecProfileLevel.AV1ProfileMain10HDR10 in profiles) add(HdrFormat.HDR10)
                if (CodecProfileLevel.AV1ProfileMain10HDR10Plus in profiles) add(HdrFormat.HDR10_PLUS)
                if (CodecProfileLevel.AV1ProfileMain10 in profiles) add(HdrFormat.HLG)
            }
            VideoCodec.DOLBY_VISION -> add(HdrFormat.DOLBY_VISION)
            VideoCodec.AVC -> Unit // 8-bit in practice: no HDR
        }
    }

    private fun display(): DisplayInfo {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val largest = display.supportedModes.maxBy { it.physicalWidth.toLong() * it.physicalHeight }
        val hdr = mode.supportedHdrTypes.toList().mapNotNull { type: Int ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> HdrFormat.HDR10
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HdrFormat.HDR10_PLUS
                Display.HdrCapabilities.HDR_TYPE_HLG -> HdrFormat.HLG
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HdrFormat.DOLBY_VISION
                else -> null
            }
        }.toSet()
        return DisplayInfo(
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            refreshRateHz = mode.refreshRate,
            maxWidthPx = largest.physicalWidth,
            maxHeightPx = largest.physicalHeight,
            hdrFormats = hdr,
        )
    }

    private fun memory(): MemoryInfo {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return MemoryInfo(
            totalRamMb = info.totalMem / (1024 * 1024),
            memoryClassMb = activityManager.memoryClass,
            isLowRam = activityManager.isLowRamDevice,
        )
    }
}
