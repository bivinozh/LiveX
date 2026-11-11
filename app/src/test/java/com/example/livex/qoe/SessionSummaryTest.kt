package com.example.livex.qoe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class SessionSummaryTest {

    private val start = QoeEvent.SessionStart("s1", 1_000, "bbb", "Big Buck Bunny", false, 1_700_000_000_000)

    private fun fold(vararg events: QoeEvent) =
        events.fold(SessionSummary.start(start)) { summary, event -> summary.applyEvent(event) }

    @Test
    fun `a full session folds into its summary`() {
        val summary = fold(
            QoeEvent.VideoStart("s1", 1_900, vstMs = 900),
            QoeEvent.BitrateSwitch("s1", 5_000, 800_000, 2_500_000, 1280, 720),
            QoeEvent.StallStart("s1", 8_000),
            QoeEvent.StallEnd("s1", 9_000, durationMs = 1_000),
            QoeEvent.PlaybackError("s1", 9_500, "LOAD_ERROR", "retrying", isFatal = false),
            QoeEvent.SessionEnd("s1", 21_000, EndReason.EXIT, playingTimeMs = 19_000, stallTimeMs = 1_000, avgBitrateBps = 2_000_000),
        )

        assertEquals(900L, summary.vstMs)
        assertEquals(1, summary.bitrateSwitches)
        assertEquals(1, summary.stallCount)
        assertEquals(1_000L, summary.stallTimeMs)
        assertEquals(0.05, summary.rebufferingRatio!!, 1e-9)
        assertEquals(2_000_000, summary.avgBitrateBps)
        assertEquals(0, summary.failures) // a retried request isn't a failure
        assertEquals(20_000L, summary.durationMs)
        assertEquals(EndReason.EXIT, summary.endReason)
        assertFalse(summary.isEbvs)
    }

    @Test
    fun `fatal errors count as failures and keep the last message`() {
        val summary = fold(QoeEvent.PlaybackError("s1", 2_000, "ERROR_CODE_IO_BAD_HTTP_STATUS", "Not found (404)", isFatal = true))

        assertEquals(1, summary.failures)
        assertEquals("Not found (404)", summary.lastError)
    }

    @Test
    fun `ebvs is flagged`() {
        val summary = fold(QoeEvent.Ebvs("s1", 5_000, waitedMs = 4_000))

        assertTrue(summary.isEbvs)
        assertNull(summary.vstMs)
    }

    @Test
    fun `stall time is known before the session ends`() {
        val summary = fold(QoeEvent.StallStart("s1", 3_000), QoeEvent.StallEnd("s1", 3_500, 500))

        assertEquals(500L, summary.stallTimeMs)
        assertNull(summary.rebufferingRatio) // playing time arrives with session_end
        assertNull(summary.endReason)
    }

    @Test
    fun `summary text for the sessions list`() {
        val summary = fold(
            QoeEvent.VideoStart("s1", 1_900, vstMs = 900),
            QoeEvent.StallStart("s1", 8_000),
            QoeEvent.StallEnd("s1", 9_000, durationMs = 1_000),
            QoeEvent.SessionEnd("s1", 61_000, EndReason.COMPLETED, 59_000, 1_000, 2_160_000),
        )

        assertEquals(
            "VST 900 ms · Rebuffering 1.7 % · Stalls 1 · Avg 2.2 Mbps · Switches 0 · Failures 0",
            summary.metricsText,
        )
        assertEquals("Nov 14, 22:13 · 1:00 · Completed", summary.headerText(ZoneOffset.UTC))
    }

    @Test
    fun `summary text for an ebvs session`() {
        val summary = fold(
            QoeEvent.Ebvs("s1", 5_200, waitedMs = 4_200),
            QoeEvent.SessionEnd("s1", 5_200, EndReason.EXIT, 0, 0, null),
        )

        assertEquals("Left before video start (EBVS) after 4.2 s · Failures 0", summary.metricsText)
    }
}
