package com.example.livex.player.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidevineInfoTest {

    @Test
    fun `L1 is hardware-backed and allowed HD`() {
        val info = WidevineInfo.Available(securityLevel = "L1", maxHdcpLevel = "HDCP-2.2", systemId = "4464")

        assertTrue(info.isHardwareSecure)
        assertEquals("Widevine L1 (hardware) · HD/UHD OK", info.label)
    }

    @Test
    fun `L3 is software-only, typically SD`() {
        val info = WidevineInfo.Available(securityLevel = "L3", maxHdcpLevel = null, systemId = null)

        assertFalse(info.isHardwareSecure)
        assertEquals("Widevine L3 (software) · usually SD only", info.label)
    }

    @Test
    fun `L2 is partly hardware`() {
        val info = WidevineInfo.Available(securityLevel = "L2", maxHdcpLevel = null, systemId = null)

        assertFalse(info.isHardwareSecure)
        assertEquals("Widevine L2 · usually SD only", info.label)
    }

    @Test
    fun `no Widevine on the device`() {
        assertEquals("No Widevine on this device", WidevineInfo.Unavailable.label)
    }

    @Test
    fun `only L1 may select HD renditions of protected content`() {
        assertEquals(null, WidevineInfo.Available("L1", null, null).maxProtectedVideoHeight)
        assertEquals(480, WidevineInfo.Available("L3", null, null).maxProtectedVideoHeight)
        assertEquals(480, WidevineInfo.Available("L2", null, null).maxProtectedVideoHeight)
        assertEquals(null, WidevineInfo.Unavailable.maxProtectedVideoHeight) // nothing will play anyway
    }

    @Test
    fun `hdcp is shown when known`() {
        assertEquals("HDCP-2.2", WidevineInfo.Available("L1", "HDCP-2.2", null).hdcpLabel)
        assertEquals("—", WidevineInfo.Available("L3", null, null).hdcpLabel)
    }
}
