package com.example.livex.player.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdSeekPolicyTest {

    private val preRoll = AdBreak(timeMs = 0, isPlayed = true)
    private val mid1 = AdBreak(timeMs = 60_000, isPlayed = false)
    private val mid2 = AdBreak(timeMs = 120_000, isPlayed = false)
    private val postRoll = AdBreak(timeMs = AdBreak.POST_ROLL, isPlayed = false)

    @Test
    fun `no ad breaks in the way means a normal seek`() {
        assertEquals(SeekOutcome.Seeked(50_000), AdSeekPolicy.resolve(fromMs = 10_000, toMs = 50_000, breaks = listOf(preRoll, mid1)))
    }

    @Test
    fun `seeking past an unwatched break plays it first, then continues to the target`() {
        assertEquals(
            SeekOutcome.SnappedToAd(adTimeMs = 60_000, resumeAtMs = 90_000),
            AdSeekPolicy.resolve(fromMs = 30_000, toMs = 90_000, breaks = listOf(preRoll, mid1, mid2)),
        )
    }

    @Test
    fun `skipping several breaks only plays the last one`() {
        assertEquals(
            SeekOutcome.SnappedToAd(adTimeMs = 120_000, resumeAtMs = 150_000),
            AdSeekPolicy.resolve(fromMs = 30_000, toMs = 150_000, breaks = listOf(mid1, mid2)),
        )
    }

    @Test
    fun `watched breaks do not block`() {
        val watched = mid1.copy(isPlayed = true)

        assertEquals(SeekOutcome.Seeked(90_000), AdSeekPolicy.resolve(fromMs = 30_000, toMs = 90_000, breaks = listOf(watched)))
    }

    @Test
    fun `backward seeks are always allowed`() {
        assertEquals(SeekOutcome.Seeked(20_000), AdSeekPolicy.resolve(fromMs = 130_000, toMs = 20_000, breaks = listOf(mid1, mid2)))
    }

    @Test
    fun `the post-roll never snaps a seek, it plays when content ends`() {
        assertEquals(SeekOutcome.Seeked(500_000), AdSeekPolicy.resolve(fromMs = 400_000, toMs = 500_000, breaks = listOf(postRoll)))
    }

    @Test
    fun `landing exactly on a break counts as reaching it`() {
        assertEquals(
            SeekOutcome.SnappedToAd(adTimeMs = 60_000, resumeAtMs = 60_000),
            AdSeekPolicy.resolve(fromMs = 30_000, toMs = 60_000, breaks = listOf(mid1)),
        )
    }
}
