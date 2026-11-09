package com.example.livex.player.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWindowTest {

    /** 60 s DVR window; the live edge position (default position) is at 50 s, i.e. 10 s target. */
    private fun window(positionMs: Long) = LiveWindow(
        windowDurationMs = 60_000,
        positionMs = positionMs,
        defaultPositionMs = 50_000,
        targetOffsetMs = 10_000,
    )

    @Test
    fun `at the default position you are live`() {
        val w = window(positionMs = 49_400)

        assertTrue(w.isAtLiveEdge)
        assertEquals("● LIVE", w.statusText)
    }

    @Test
    fun `behind the default position shows how far`() {
        val w = window(positionMs = 37_600)

        assertFalse(w.isAtLiveEdge)
        assertEquals(12_400L, w.behindLiveMs)
        assertEquals("−12.4 s behind live", w.statusText)
    }

    @Test
    fun `fractions for the seek bar`() {
        val w = window(positionMs = 30_000)

        assertEquals(0.5f, w.positionFraction, 1e-6f)
        assertEquals(50_000f / 60_000f, w.liveEdgeFraction, 1e-6f)
    }

    @Test
    fun `window length label`() {
        assertEquals("DVR window 60 s", window(0).windowLabel)
        assertEquals("DVR window 5:00", window(0).copy(windowDurationMs = 300_000).windowLabel)
    }

    @Test
    fun `positions outside the window are clamped`() {
        assertEquals(1f, window(positionMs = 70_000).positionFraction, 1e-6f)
        assertEquals(0f, window(positionMs = -5_000).positionFraction, 1e-6f)
    }
}
