package com.example.livex.qoe

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
            ),
            stats,
        )
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
    fun `unknown values show a dash`() {
        val stats = StatsState()

        assertEquals("—", rowValue(stats, StatsState.RESOLUTION))
        assertEquals("—", rowValue(stats, StatsState.VIDEO_BITRATE))
        assertEquals("—", rowValue(stats, StatsState.BANDWIDTH))
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
                StatsState.SPEED,
                StatsState.DROPPED_FRAMES,
                StatsState.LOAD_ERRORS,
            ),
            StatsState().rows().map { it.label },
        )
    }
}
