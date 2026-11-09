package com.example.livex.qoe

import com.example.livex.player.ads.AdBreakKind
import com.example.livex.player.ads.AdEventLog
import com.example.livex.player.ads.AdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsStateTest {

    private val metrics = QoeMetrics(
        playbackState = "Ready",
        videoWidth = 1280,
        videoHeight = 720,
        videoBitrateBps = 2_500_000,
        bandwidthEstimateBps = 8_400_000,
        droppedFrames = 3,
        loadErrorCount = 1,
        stalls = StallStats(
            startupTimeMs = 840,
            stallCount = 2,
            stallTimeMs = 1_500,
            seekBufferingCount = 1,
            seekBufferingTimeMs = 900,
            playingTimeMs = 58_500,
        ),
    )

    private fun rowValue(stats: StatsState, label: String) = stats.rows().single { it.label == label }.value

    @Test
    fun `combines event metrics with the polled player sample`() {
        val stats = StatsState.from(
            metrics,
            PlayerSample(bufferedDurationMs = 21_400, liveOffsetMs = 6_200, playbackSpeed = 1.03f),
        )

        assertEquals(
            StatsState(
                playerState = "Ready",
                videoWidth = 1280,
                videoHeight = 720,
                videoBitrateBps = 2_500_000,
                bandwidthEstimateBps = 8_400_000,
                bufferedDurationMs = 21_400,
                liveOffsetMs = 6_200,
                playbackSpeed = 1.03f,
                droppedFrames = 3,
                loadErrorCount = 1,
                stalls = metrics.stalls,
            ),
            stats,
        )
    }

    @Test
    fun `buffer history is passed through for the chart`() {
        val history = listOf(BufferPoint(atMs = 0, bufferedMs = 4_000, isStalled = false))

        val stats = StatsState.from(metrics, PlayerSample(0, null, 1f), history)

        assertEquals(history, stats.bufferHistory)
    }

    @Test
    fun `stall rows show startup, stalls, ratio and seek buffering`() {
        val stats = StatsState.from(metrics, PlayerSample(0, null, 1f))

        assertEquals("840 ms", rowValue(stats, StatsState.STARTUP))
        assertEquals("2 (1.5 s)", rowValue(stats, StatsState.STALLS))
        assertEquals("2.50%", rowValue(stats, StatsState.REBUFFER_RATIO)) // 1.5 / (58.5 + 1.5)
        assertEquals("1 (0.9 s)", rowValue(stats, StatsState.SEEK_BUFFERING))
    }

    @Test
    fun `ongoing stall is marked`() {
        val stats = StatsState(stalls = StallStats(stallCount = 1, stallTimeMs = 2_000, isStalled = true))

        assertEquals("1 (2.0 s) ●", rowValue(stats, StatsState.STALLS))
    }

    @Test
    fun `vod has no live offset`() {
        val stats = StatsState.from(metrics, PlayerSample(bufferedDurationMs = 0, liveOffsetMs = null, playbackSpeed = 1f))

        assertNull(stats.liveOffsetMs)
        assertEquals("— (VOD)", rowValue(stats, StatsState.LIVE_OFFSET))
    }

    @Test
    fun `rows are formatted for display`() {
        val stats = StatsState.from(
            metrics,
            PlayerSample(bufferedDurationMs = 21_400, liveOffsetMs = 6_200, playbackSpeed = 1.03f),
        )

        assertEquals("Ready", rowValue(stats, StatsState.STATE))
        assertEquals("1280×720", rowValue(stats, StatsState.RESOLUTION))
        assertEquals("2.50 Mbps", rowValue(stats, StatsState.VIDEO_BITRATE))
        assertEquals("8.40 Mbps", rowValue(stats, StatsState.BANDWIDTH))
        assertEquals("21.4 s", rowValue(stats, StatsState.BUFFER))
        assertEquals("6.2 s", rowValue(stats, StatsState.LIVE_OFFSET))
        assertEquals("1.03×", rowValue(stats, StatsState.SPEED))
        assertEquals("3", rowValue(stats, StatsState.DROPPED_FRAMES))
        assertEquals("1", rowValue(stats, StatsState.LOAD_ERRORS))
    }

    @Test
    fun `negative live offset is flagged as a device clock problem`() {
        // Seen on an emulator whose clock was ~10 h behind: offset = device clock - PROGRAM-DATE-TIME.
        val stats = StatsState(liveOffsetMs = -37_501_400)

        assertEquals(StatsState.CLOCK_SKEW, rowValue(stats, StatsState.LIVE_OFFSET))
    }

    @Test
    fun `target offset is shown for live, dash for VOD`() {
        assertEquals("10.0 s", rowValue(StatsState(liveOffsetMs = 9_000, targetOffsetMs = 10_000), StatsState.TARGET_OFFSET))
        assertEquals("—", rowValue(StatsState(), StatsState.TARGET_OFFSET))
    }

    @Test
    fun `live speed shows what the speed control is doing`() {
        val live = StatsState(liveOffsetMs = 12_000, targetOffsetMs = 10_000)

        assertEquals("1.03× ▲ catching up", rowValue(live.copy(playbackSpeed = 1.03f), StatsState.SPEED))
        assertEquals("0.97× ▼ falling back", rowValue(live.copy(playbackSpeed = 0.97f), StatsState.SPEED))
        assertEquals("1.00× on target", rowValue(live.copy(playbackSpeed = 1f), StatsState.SPEED))
        assertEquals("1.00×", rowValue(StatsState(playbackSpeed = 1f), StatsState.SPEED)) // VOD
    }

    @Test
    fun `ad row shows the ad being played, a dash during content`() {
        val ad = AdState(isPlayingAd = true, breakKind = AdBreakKind.POST_ROLL, adIndexInBreak = 0, adsInBreak = 1)

        assertEquals("post-roll 1/1", rowValue(StatsState(ad = ad), StatsState.AD))
        assertEquals("post-roll 1/1 · 8 s", rowValue(StatsState(ad = ad.copy(remainingMs = 7_200)), StatsState.AD))
        assertEquals("—", rowValue(StatsState(), StatsState.AD))
    }

    @Test
    fun `ad events are passed through for the overlay`() {
        val events = listOf(AdEventLog.Entry("STARTED", isError = false))

        val stats = StatsState.from(metrics, PlayerSample(0, null, 1f), adEvents = events)

        assertEquals(events, stats.adEvents)
    }

    @Test
    fun `unknown values show a dash`() {
        val stats = StatsState()

        assertEquals("—", rowValue(stats, StatsState.RESOLUTION))
        assertEquals("—", rowValue(stats, StatsState.VIDEO_BITRATE))
        assertEquals("—", rowValue(stats, StatsState.BANDWIDTH))
        assertEquals("—", rowValue(stats, StatsState.STARTUP))
        assertEquals("—", rowValue(stats, StatsState.REBUFFER_RATIO))
    }

    @Test
    fun `rows keep a stable order`() {
        assertEquals(
            listOf(
                StatsState.STATE,
                StatsState.RESOLUTION,
                StatsState.VIDEO_BITRATE,
                StatsState.BANDWIDTH,
                StatsState.BUFFER,
                StatsState.LIVE_OFFSET,
                StatsState.TARGET_OFFSET,
                StatsState.SPEED,
                StatsState.DROPPED_FRAMES,
                StatsState.LOAD_ERRORS,
                StatsState.STARTUP,
                StatsState.STALLS,
                StatsState.REBUFFER_RATIO,
                StatsState.SEEK_BUFFERING,
                StatsState.AD,
            ),
            StatsState().rows().map { it.label },
        )
    }
}
