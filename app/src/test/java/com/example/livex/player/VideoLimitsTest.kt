package com.example.livex.player

import com.example.livex.player.drm.WidevineInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoLimitsTest {

    private val l1 = WidevineInfo.Available("L1", "HDCP-2.2", "1")
    private val l3 = WidevineInfo.Available("L3", null, null)
    private val unlimited = Int.MAX_VALUE to Int.MAX_VALUE

    @Test
    fun `clear stream without data saver is unlimited`() {
        assertEquals(unlimited, VideoLimits.maxVideoSize(dataSaver = false, isProtected = false, widevine = l3))
    }

    @Test
    fun `data saver caps at 720p`() {
        assertEquals(1280 to 720, VideoLimits.maxVideoSize(dataSaver = true, isProtected = false, widevine = null))
    }

    @Test
    fun `protected stream on L3 is capped at SD`() {
        assertEquals(Int.MAX_VALUE to 480, VideoLimits.maxVideoSize(dataSaver = false, isProtected = true, widevine = l3))
    }

    @Test
    fun `protected stream on L1 is not capped`() {
        assertEquals(unlimited, VideoLimits.maxVideoSize(dataSaver = false, isProtected = true, widevine = l1))
    }

    @Test
    fun `unknown security level assumes SD until the off-main-thread read finishes`() {
        assertEquals(Int.MAX_VALUE to 480, VideoLimits.maxVideoSize(dataSaver = false, isProtected = true, widevine = null))
    }

    @Test
    fun `data saver and the DRM cap combine instead of replacing each other`() {
        assertEquals(1280 to 480, VideoLimits.maxVideoSize(dataSaver = true, isProtected = true, widevine = l3))
    }
}
