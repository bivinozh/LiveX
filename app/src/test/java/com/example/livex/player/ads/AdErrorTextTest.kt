package com.example.livex.player.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdErrorTextTest {

    @Test
    fun `wrapper without an ad is explained in one short line`() {
        assertEquals(
            "303 VAST_NO_ADS_AFTER_WRAPPER · wrapper led to no ad → skipped",
            AdErrorText.overlay(codeName = "VAST_NO_ADS_AFTER_WRAPPER", codeNumber = 303),
        )
    }

    @Test
    fun `common codes get a plain explanation`() {
        assertEquals("ad video didn't start in time", AdErrorText.explain("VAST_MEDIA_LOAD_TIMEOUT"))
        assertEquals("empty VAST: no ad to show", AdErrorText.explain("VAST_EMPTY_RESPONSE"))
        assertEquals("no ad file this device can play", AdErrorText.explain("VAST_LINEAR_ASSET_MISMATCH"))
        assertEquals("ad server didn't answer in time", AdErrorText.explain("VAST_LOAD_TIMEOUT"))
        assertEquals("ad request failed (network)", AdErrorText.explain("FAILED_TO_REQUEST_ADS"))
    }

    @Test
    fun `unknown codes still say what the player did`() {
        assertEquals(
            "999 SOMETHING_NEW · ad error → skipped",
            AdErrorText.overlay(codeName = "SOMETHING_NEW", codeNumber = 999),
        )
    }

    @Test
    fun `logcat line keeps the full IMA message`() {
        assertEquals(
            "303 VAST_NO_ADS_AFTER_WRAPPER: No Ads VAST response after one or more Wrappers. Ad skipped, content continues.",
            AdErrorText.logcat("VAST_NO_ADS_AFTER_WRAPPER", 303, "No Ads VAST response after one or more Wrappers."),
        )
        // IMA's messages don't always end with a period (seen on device).
        assertEquals(
            "303 VAST_NO_ADS_AFTER_WRAPPER: No Ads VAST response after one or more Wrappers. Ad skipped, content continues.",
            AdErrorText.logcat("VAST_NO_ADS_AFTER_WRAPPER", 303, "No Ads VAST response after one or more Wrappers"),
        )
    }
}
