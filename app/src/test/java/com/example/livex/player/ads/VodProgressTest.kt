package com.example.livex.player.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class VodProgressTest {

    private val progress = VodProgress(
        positionMs = 83_000,
        durationMs = 596_000,
        adBreaks = listOf(
            AdBreak(timeMs = 0, isPlayed = true),
            AdBreak(timeMs = 298_000, isPlayed = false),
            AdBreak(timeMs = AdBreak.POST_ROLL, isPlayed = false),
        ),
    )

    @Test
    fun `time label`() {
        assertEquals("1:23 / 9:56", progress.timeLabel)
    }

    @Test
    fun `ad break summary`() {
        assertEquals("3 ad breaks · 1 watched", progress.adSummary)
        assertEquals("No ad breaks", progress.copy(adBreaks = emptyList()).adSummary)
    }

    @Test
    fun `marker positions on the bar`() {
        assertEquals(listOf(0f, 0.5f, 1f), progress.adBreaks.map { progress.fractionOf(it) })
        assertEquals(83_000f / 596_000f, progress.positionFraction, 1e-6f)
    }

    @Test
    fun `unknown duration keeps everything at the start`() {
        val unknown = progress.copy(durationMs = 0)

        assertEquals(0f, unknown.positionFraction, 0f)
        assertEquals("1:23 / --:--", unknown.timeLabel)
    }
}
