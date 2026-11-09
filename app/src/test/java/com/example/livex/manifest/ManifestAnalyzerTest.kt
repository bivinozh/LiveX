package com.example.livex.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestAnalyzerTest {

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=700000,BANDWIDTH=800000,RESOLUTION=640x360
        low/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
        https://cdn.example.com/hd/index.m3u8
    """.trimIndent()

    private val liveMedia = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:1234
        #EXTINF:6.000,
        seg1234.ts
        #EXTINF:6.000,
        seg1235.ts
    """.trimIndent()

    private val vodMedia = "$liveMedia\n#EXT-X-ENDLIST"

    private val liveMpd = """
        <?xml version="1.0"?>
        <MPD type="dynamic" availabilityStartTime="1970-01-01T00:00:00Z" minimumUpdatePeriod="PT2S" timeShiftBufferDepth="PT5M">
          <Period id="p0">
            <AdaptationSet contentType="video">
              <Representation id="v1" bandwidth="300000"/>
              <Representation id="v2" bandwidth="1200000"/>
            </AdaptationSet>
            <AdaptationSet contentType="audio">
              <Representation id="a1" bandwidth="48000"/>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun highlighted(text: String, kind: HighlightKind): List<String> =
        ManifestAnalyzer.highlights(text).filter { it.kind == kind }.map { text.substring(it.start, it.end) }

    // --- Summary ---

    @Test
    fun `master playlist summary counts variants`() {
        assertEquals(ManifestSummary.HlsMaster(variantCount = 2), ManifestAnalyzer.summarize(master))
    }

    @Test
    fun `live media playlist summary`() {
        assertEquals(
            ManifestSummary.HlsMedia(targetDurationSec = 6, mediaSequence = 1234, segmentCount = 2, hasEndList = false),
            ManifestAnalyzer.summarize(liveMedia),
        )
    }

    @Test
    fun `vod media playlist has end list`() {
        assertTrue((ManifestAnalyzer.summarize(vodMedia) as ManifestSummary.HlsMedia).hasEndList)
    }

    @Test
    fun `dash summary counts the hierarchy and live attributes`() {
        assertEquals(
            ManifestSummary.Dash(
                isDynamic = true,
                periods = 1,
                adaptationSets = 2,
                representations = 3,
                minimumUpdatePeriod = "PT2S",
                timeShiftBufferDepth = "PT5M",
            ),
            ManifestAnalyzer.summarize(liveMpd),
        )
    }

    @Test
    fun `unrecognized text`() {
        assertEquals(ManifestSummary.Unknown, ManifestAnalyzer.summarize("hello"))
    }

    // --- Variants ---

    @Test
    fun `variants resolve relative and absolute uris with a readable label`() {
        val variants = ManifestAnalyzer.hlsVariants(master, baseUrl = "https://example.com/stream/master.m3u8")

        assertEquals(
            listOf(
                HlsVariant(label = "640x360 · 800 kbps", url = "https://example.com/stream/low/index.m3u8"),
                HlsVariant(label = "1280x720 · 2500 kbps", url = "https://cdn.example.com/hd/index.m3u8"),
            ),
            variants,
        )
    }

    @Test
    fun `media playlist has no variants`() {
        assertTrue(ManifestAnalyzer.hlsVariants(liveMedia, "https://example.com/a.m3u8").isEmpty())
    }

    // --- Highlights ---

    @Test
    fun `hls structure tags are highlighted`() {
        val structure = highlighted(master + "\n" + liveMedia, HighlightKind.STRUCTURE)

        assertTrue(structure.contains("#EXT-X-STREAM-INF"))
        assertTrue(structure.contains("#EXT-X-TARGETDURATION:6"))
        assertTrue(structure.contains("#EXTINF:6.000"))
    }

    @Test
    fun `hls bandwidth and resolution highlighted but not average bandwidth`() {
        val attributes = highlighted(master, HighlightKind.ATTRIBUTE)

        assertTrue(attributes.contains("BANDWIDTH=800000"))
        assertTrue(attributes.contains("RESOLUTION=640x360"))
        assertFalse(attributes.contains("BANDWIDTH=700000"))
    }

    @Test
    fun `hls media sequence is a live marker`() {
        assertEquals(listOf("#EXT-X-MEDIA-SEQUENCE:1234"), highlighted(liveMedia, HighlightKind.LIVE))
    }

    @Test
    fun `dash elements attributes and live markers are highlighted`() {
        val structure = highlighted(liveMpd, HighlightKind.STRUCTURE)
        assertTrue(structure.containsAll(listOf("<Period", "</Period", "<AdaptationSet", "<Representation")))

        assertTrue(highlighted(liveMpd, HighlightKind.ATTRIBUTE).contains("bandwidth=\"300000\""))

        assertEquals(
            listOf(
                "type=\"dynamic\"",
                "availabilityStartTime=\"1970-01-01T00:00:00Z\"",
                "minimumUpdatePeriod=\"PT2S\"",
                "timeShiftBufferDepth=\"PT5M\"",
            ),
            highlighted(liveMpd, HighlightKind.LIVE),
        )
    }

    @Test
    fun `highlights are sorted and do not overlap`() {
        val ranges = ManifestAnalyzer.highlights(master + "\n" + liveMpd)

        ranges.zipWithNext().forEach { (a, b) -> assertTrue("$a overlaps $b", a.end <= b.start) }
    }
}
