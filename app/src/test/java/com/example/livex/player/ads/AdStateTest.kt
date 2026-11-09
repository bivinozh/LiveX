package com.example.livex.player.ads

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdStateTest {

    @Test
    fun `break kind from the ad group time`() {
        assertEquals(AdBreakKind.PRE_ROLL, AdBreakKind.fromAdGroupTimeUs(0))
        assertEquals(AdBreakKind.MID_ROLL, AdBreakKind.fromAdGroupTimeUs(15_000_000))
        assertEquals(AdBreakKind.POST_ROLL, AdBreakKind.fromAdGroupTimeUs(C.TIME_END_OF_SOURCE))
    }

    @Test
    fun `no label while content plays`() {
        assertNull(AdState().label)
    }

    @Test
    fun `label while an ad plays`() {
        val state = AdState(isPlayingAd = true, breakKind = AdBreakKind.MID_ROLL, adIndexInBreak = 1, adsInBreak = 3, remainingMs = 7_400)

        assertEquals("AD · mid-roll · 2 of 3 · 8 s left", state.label)
        assertEquals("AD 2/3 · 8 s", state.badge)
    }

    @Test
    fun `unknown remaining time is left out`() {
        val state = AdState(isPlayingAd = true, breakKind = AdBreakKind.PRE_ROLL, adIndexInBreak = 0, adsInBreak = 1)

        assertEquals("AD · pre-roll · 1 of 1", state.label)
        assertEquals("AD 1/1", state.badge)
    }
}
