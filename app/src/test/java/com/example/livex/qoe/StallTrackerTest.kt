package com.example.livex.qoe

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StallTrackerTest {

    private lateinit var tracker: StallTracker

    @Before
    fun setUp() {
        tracker = StallTracker()
    }

    /** play request at 0 ms, first frame + READY at 800 ms. */
    private fun startPlayback() {
        tracker.reset(nowMs = 0)
        tracker.onPlayWhenReadyChanged(true, nowMs = 0)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 0)
        tracker.onFirstFrameRendered(nowMs = 800)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 800)
    }

    // --- Startup (VST) ---

    @Test
    fun `startup time is play request to first frame`() {
        startPlayback()

        assertEquals(800L, tracker.snapshot(nowMs = 1_000).startupTimeMs)
    }

    @Test
    fun `startup time is measured once`() {
        startPlayback()
        tracker.onFirstFrameRendered(nowMs = 9_000) // e.g. surface re-attached after rotation

        assertEquals(800L, tracker.snapshot(nowMs = 10_000).startupTimeMs)
    }

    @Test
    fun `no startup time before the first frame`() {
        tracker.reset(nowMs = 0)
        tracker.onPlayWhenReadyChanged(true, nowMs = 0)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 0)

        assertNull(tracker.snapshot(nowMs = 5_000).startupTimeMs)
    }

    @Test
    fun `initial buffering is startup, not a stall`() {
        startPlayback()

        val stats = tracker.snapshot(nowMs = 1_000)
        assertEquals(0, stats.stallCount)
        assertEquals(0L, stats.stallTimeMs)
    }

    @Test
    fun `first frame rendered while still buffering does not turn startup into a stall`() {
        tracker.reset(nowMs = 0)
        tracker.onPlayWhenReadyChanged(true, nowMs = 0)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 0)
        tracker.onFirstFrameRendered(nowMs = 700) // first frame can render before READY
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 900)

        assertEquals(0, tracker.snapshot(nowMs = 1_000).stallCount)
    }

    // --- Stalls ---

    @Test
    fun `buffering after playback started is a stall`() {
        startPlayback()

        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 5_400)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 8_000)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 8_100)

        val stats = tracker.snapshot(nowMs = 9_000)
        assertEquals(2, stats.stallCount)
        assertEquals(500L, stats.stallTimeMs)
        assertEquals(0, stats.seekBufferingCount)
    }

    @Test
    fun `an ongoing stall is included and flagged`() {
        startPlayback()
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000)

        val stats = tracker.snapshot(nowMs = 6_500)
        assertTrue(stats.isStalled)
        assertEquals(1_500L, stats.stallTimeMs)
    }

    // --- Seeks ---

    @Test
    fun `buffering right after a seek is seek buffering, not a stall`() {
        startPlayback()

        tracker.onSeek(nowMs = 5_000)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_010)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 6_210)

        val stats = tracker.snapshot(nowMs = 7_000)
        assertEquals(0, stats.stallCount)
        assertEquals(0L, stats.stallTimeMs)
        assertEquals(1, stats.seekBufferingCount)
        assertEquals(1_200L, stats.seekBufferingTimeMs)
        assertFalse(stats.isStalled)
    }

    @Test
    fun `buffering after the seek has recovered is a real stall again`() {
        startPlayback()
        tracker.onSeek(nowMs = 5_000)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_010)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 5_500)

        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_900)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 6_300)

        val stats = tracker.snapshot(nowMs = 7_000)
        assertEquals(1, stats.seekBufferingCount)
        assertEquals(1, stats.stallCount)
        assertEquals(400L, stats.stallTimeMs)
    }

    @Test
    fun `a seek long ago does not excuse a later stall`() {
        startPlayback()
        tracker.onSeek(nowMs = 5_000) // seek inside the buffer: no BUFFERING, no new READY

        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 20_000)

        assertEquals(1, tracker.snapshot(nowMs = 21_000).stallCount)
    }

    // --- Paused ---

    @Test
    fun `buffering while paused is not a stall the viewer experiences`() {
        startPlayback()
        tracker.onPlayWhenReadyChanged(false, nowMs = 3_000)

        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 4_000)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 6_000)

        assertEquals(0, tracker.snapshot(nowMs = 7_000).stallCount)
    }

    @Test
    fun `pressing play while still buffering counts the stall once`() {
        startPlayback()
        tracker.onPlayWhenReadyChanged(false, nowMs = 3_000)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 4_000)

        tracker.onPlayWhenReadyChanged(true, nowMs = 5_000)
        tracker.onPlayWhenReadyChanged(false, nowMs = 5_300)
        tracker.onPlayWhenReadyChanged(true, nowMs = 5_600)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 5_800)

        val stats = tracker.snapshot(nowMs = 7_000)
        assertEquals(1, stats.stallCount)
        assertEquals(500L, stats.stallTimeMs) // 5000-5300 + 5600-5800; the paused gap doesn't count
    }

    // --- Rebuffering ratio ---

    @Test
    fun `rebuffering ratio is stall time over watch time`() {
        startPlayback() // playing from 800
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 9_800) // 9 s played
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 10_800) // 1 s stalled

        val stats = tracker.snapshot(nowMs = 10_800)
        assertEquals(9_000L, stats.playingTimeMs)
        assertEquals(0.1, stats.rebufferingRatio!!, 1e-9)
    }

    @Test
    fun `seek buffering and paused time are excluded from the ratio`() {
        startPlayback() // playing from 800
        tracker.onSeek(nowMs = 2_800) // 2 s played
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 2_800)
        tracker.onPlaybackStateChanged(Player.STATE_READY, nowMs = 4_800) // 2 s seek buffering
        tracker.onPlayWhenReadyChanged(false, nowMs = 6_800) // 2 s played, then paused
        tracker.onPlayWhenReadyChanged(true, nowMs = 60_000)

        val stats = tracker.snapshot(nowMs = 60_000)
        assertEquals(4_000L, stats.playingTimeMs)
        assertEquals(0.0, stats.rebufferingRatio!!, 1e-9)
    }

    @Test
    fun `no ratio before anything was watched`() {
        assertNull(tracker.snapshot(nowMs = 0).rebufferingRatio)
    }

    @Test
    fun `reset starts a fresh measurement`() {
        startPlayback()
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000)

        tracker.reset(nowMs = 10_000)

        assertEquals(StallStats(), tracker.snapshot(nowMs = 11_000))
    }
}
