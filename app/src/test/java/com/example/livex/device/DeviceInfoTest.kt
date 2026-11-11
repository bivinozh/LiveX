package com.example.livex.device

import com.example.livex.player.drm.WidevineInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoTest {

    private val hd = DisplayInfo(1920, 1080, 60f, 1920, 1080, emptySet())
    private val uhdHdr = DisplayInfo(1920, 1080, 60f, 3840, 2160, setOf(HdrFormat.HDR10, HdrFormat.HLG))
    private val memory = MemoryInfo(totalRamMb = 2048, memoryClassMb = 192, isLowRam = false)

    private fun device(
        entries: List<DecoderEntry>,
        display: DisplayInfo = hd,
        widevine: WidevineInfo? = null,
    ) = DeviceInfo("Test TV", 36, true, CodecSupport.merge(entries), display, memory, widevine)

    private val hwAvc = DecoderEntry(VideoCodec.AVC, "c2.vendor.avc.decoder", isHardware = true, supports4k = false)
    private val swAvc = DecoderEntry(VideoCodec.AVC, "c2.android.avc.decoder", isHardware = false, supports4k = false)
    private val hwHevc4k = DecoderEntry(
        VideoCodec.HEVC, "c2.vendor.hevc.decoder", isHardware = true, supports4k = true,
        hdrFormats = setOf(HdrFormat.HDR10, HdrFormat.HLG), isTunneled = true,
    )
    private val hwHevcSecure = DecoderEntry(VideoCodec.HEVC, "c2.vendor.hevc.decoder.secure", true, true, isSecure = true)

    @Test
    fun `hardware decoders win over software ones and features are merged`() {
        val codecs = CodecSupport.merge(listOf(swAvc, hwAvc, hwHevc4k, hwHevcSecure))

        assertEquals(listOf(VideoCodec.AVC, VideoCodec.HEVC), codecs.map { it.codec })
        assertEquals(listOf("c2.vendor.avc.decoder"), codecs[0].decoderNames)
        val hevc = codecs[1]
        assertTrue(hevc.isTunneled && hevc.isSecure && hevc.supports4k)
        assertEquals("HW · 4K · HDR10, HLG · tunneled · secure", hevc.featureText)
    }

    @Test
    fun `software only codec is labelled`() {
        assertEquals("software only", CodecSupport.merge(listOf(swAvc)).single().featureText)
    }

    @Test
    fun `4K needs both a 4K hardware decoder and a 4K display mode`() {
        assertTrue(device(listOf(hwHevc4k), uhdHdr).canPlay4k)

        val hdTv = device(listOf(hwHevc4k), hd)
        assertTrue(hdTv.canDecode4k)
        assertFalse(hdTv.canPlay4k)
        assertEquals("Decoder yes, display no (1920×1080)", hdTv.summaryRows().first().value)
    }

    @Test
    fun `HDR is what decoder and display both support`() {
        val tv = device(listOf(hwHevc4k), uhdHdr.copy(hdrFormats = setOf(HdrFormat.HLG, HdrFormat.DOLBY_VISION)))

        assertEquals(setOf(HdrFormat.HLG), tv.playableHdr)
        assertEquals("No", device(listOf(hwAvc)).summaryRows()[2].value)
        assertEquals("Decoder yes, display no", device(listOf(hwHevc4k), hd).summaryRows()[2].value)
    }

    @Test
    fun `HDR from a software-only decoder doesn't count`() {
        // Seen on the TV emulator: only the software AV1 decoder offers Main10 (HLG).
        val swAv1 = DecoderEntry(VideoCodec.AV1, "c2.android.av1.decoder", false, false, setOf(HdrFormat.HLG))

        assertEquals("No", device(listOf(hwAvc, swAv1), uhdHdr).summaryRows()[2].value)
    }

    @Test
    fun `summary rows for a typical HD emulator`() {
        val rows = device(listOf(hwAvc, swAvc), widevine = WidevineInfo.Available("L3", "HDCP-NONE", "1")).summaryRows()

        assertEquals(
            listOf(
                "4K (2160p)" to "No",
                "HEVC hardware decoding" to "No",
                "HDR" to "No",
                "Tunneled playback" to "Not supported",
                "Widevine" to "Widevine L3 (software) · usually SD only",
            ),
            rows.map { it.label to it.value },
        )
        assertEquals(false, rows.last().isGood)
    }

    @Test
    fun `memory rows flag low-RAM devices`() {
        val lowRam = device(listOf(hwAvc)).copy(memory = MemoryInfo(1024, 96, isLowRam = true))

        assertEquals("Low-RAM device" to "Yes: use small buffers", lowRam.memoryRows().last().let { it.label to it.value })
        assertEquals("1.0 GB", lowRam.memoryRows().first().value)
    }

    @Test
    fun `codec lookup by mime`() {
        assertEquals(VideoCodec.HEVC, VideoCodec.fromMime("video/hevc"))
        assertEquals(null, VideoCodec.fromMime("video/mp4v-es"))
    }
}
