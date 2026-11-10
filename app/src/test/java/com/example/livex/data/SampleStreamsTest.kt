package com.example.livex.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleStreamsTest {

    @Test
    fun `stream ids are unique`() {
        val ids = SampleStreams.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `ad samples cover pre-roll, VMAP pre-mid-post, post-roll and an ad error`() {
        val ads = SampleStreams.all.filter { it.adTagUri != null }

        assertEquals(
            listOf(SampleStreams.adsPreRoll, SampleStreams.adsVmapPreMidPost, SampleStreams.adsPostRoll, SampleStreams.adsError),
            ads,
        )
        assertTrue(SampleStreams.adsPreRoll.adTagUri!!.contains("output=vast"))
        assertTrue(SampleStreams.adsVmapPreMidPost.adTagUri!!.contains("output=vmap"))
        assertTrue(SampleStreams.adsPostRoll.adTagUri!!.contains("sample_ar%3Dpostonly"))
    }

    @Test
    fun `client-side ads are only used with VOD content`() {
        SampleStreams.all.filter { it.adTagUri != null }.forEach { assertFalse(it.title, it.isLive) }
    }

    @Test
    fun `server-side ad samples cover HLS VOD, HLS live and multi-period DASH VOD`() {
        val dai = SampleStreams.all.filter { it.dai != null }

        assertEquals(listOf(SampleStreams.daiVodHls, SampleStreams.daiLiveHls, SampleStreams.daiVodDash), dai)
        assertEquals(DaiStream.Vod(contentSourceId = "2528370", videoId = "tears-of-steel"), SampleStreams.daiVodHls.dai)
        assertEquals(DaiStream.Live(assetKey = "sN_IYUG8STe1ZzhIIE_ksA"), SampleStreams.daiLiveHls.dai)
        assertTrue(SampleStreams.daiLiveHls.isLive)
        assertEquals("DASH · VOD · SSAI", SampleStreams.daiVodDash.formatLabel)
    }

    @Test
    fun `a stream uses client-side or server-side ads, never both`() {
        SampleStreams.all.forEach { assertFalse(it.title, it.adTagUri != null && it.dai != null) }
    }

    @Test
    fun `ad samples are labelled as such`() {
        assertEquals("HLS · VOD · Ads", SampleStreams.adsPreRoll.formatLabel)
        assertNull(SampleStreams.bigBuckBunny.adTagUri)
        assertEquals("HLS · VOD", SampleStreams.bigBuckBunny.formatLabel)
    }
}
