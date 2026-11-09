package com.example.livex.qoe

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class QoeCalculatorTest {

    private lateinit var calculator: QoeCalculator

    @Before
    fun setUp() {
        calculator = QoeCalculator().apply { reset(nowMs = 1_000) }
    }

    // Detailed startup/stall rules are covered by StallTrackerTest; these check the wiring.

    @Test
    fun `startup time comes from the stall tracker`() {
        calculator.onFirstFrameRendered(nowMs = 1_850)

        assertEquals(850L, calculator.metrics.stalls.startupTimeMs)
    }

    @Test
    fun `stalls and seek buffering are reported separately`() {
        calculator.onPlayWhenReadyChanged(true, nowMs = 1_000)
        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 1_000)
        calculator.onFirstFrameRendered(nowMs = 1_800)
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 1_800)

        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000) // stall
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 5_400)
        calculator.onSeek(nowMs = 8_000)
        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 8_000) // seek buffering
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 9_000)

        val stalls = calculator.metrics.stalls
        assertEquals(1, stalls.stallCount)
        assertEquals(400L, stalls.stallTimeMs)
        assertEquals(1, stalls.seekBufferingCount)
        assertEquals("Ready", calculator.metrics.playbackState)
    }

    @Test
    fun `an ongoing stall grows on each tick`() {
        calculator.onPlayWhenReadyChanged(true, nowMs = 1_000)
        calculator.onFirstFrameRendered(nowMs = 1_800)
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 1_800)
        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000)

        calculator.onTick(bufferedDurationMs = 0, nowMs = 6_000)

        assertEquals(1_000L, calculator.metrics.stalls.stallTimeMs)
    }

    @Test
    fun `quality switch counted only when format changes`() {
        calculator.onVideoFormatChanged(640, 360, 800_000)
        calculator.onVideoFormatChanged(640, 360, 800_000)
        calculator.onVideoFormatChanged(1280, 720, 2_500_000)

        assertEquals(1, calculator.metrics.qualitySwitches)
        assertEquals(1280, calculator.metrics.videoWidth)
        assertEquals(2_500_000, calculator.metrics.videoBitrateBps)
    }

    @Test
    fun `load events accumulate requests bytes and time`() {
        calculator.onLoadCompleted(bytesLoaded = 1_000, loadDurationMs = 100)
        calculator.onLoadCompleted(bytesLoaded = 3_000, loadDurationMs = 300)

        assertEquals(2, calculator.metrics.requestCount)
        assertEquals(4_000L, calculator.metrics.bytesLoaded)
        assertEquals("200 ms", calculator.metrics.formatted(MetricKey.AVG_REQUEST_TIME))
    }

    @Test
    fun `load errors are counted`() {
        calculator.onLoadError()
        calculator.onLoadError()

        assertEquals(2, calculator.metrics.loadErrorCount)
    }

    @Test
    fun `reset starts a fresh measurement`() {
        calculator.onFirstFrameRendered(nowMs = 1_850)
        calculator.onDroppedFrames(5)

        calculator.reset(nowMs = 10_000)

        assertNull(calculator.metrics.stalls.startupTimeMs)
        assertEquals(0, calculator.metrics.droppedFrames)
    }
}
