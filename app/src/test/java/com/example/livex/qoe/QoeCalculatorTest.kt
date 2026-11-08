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

    @Test
    fun `startup time is prepare to first frame`() {
        calculator.onFirstFrameRendered(nowMs = 1_850)

        assertEquals(850L, calculator.metrics.startupTimeMs)
    }

    @Test
    fun `startup time is measured only once`() {
        calculator.onFirstFrameRendered(nowMs = 1_850)
        calculator.onFirstFrameRendered(nowMs = 9_000) // e.g. surface re-attached after rotation

        assertEquals(850L, calculator.metrics.startupTimeMs)
    }

    @Test
    fun `initial buffering is not counted as a rebuffer`() {
        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 1_000)
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 1_800)

        assertEquals(0, calculator.metrics.rebufferCount)
        assertEquals(0L, calculator.metrics.rebufferTimeMs)
    }

    @Test
    fun `buffering after playback started counts rebuffer count and time`() {
        calculator.onFirstFrameRendered(nowMs = 1_800)

        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 5_000)
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 5_400)
        calculator.onPlaybackStateChanged(Player.STATE_BUFFERING, nowMs = 8_000)
        calculator.onPlaybackStateChanged(Player.STATE_READY, nowMs = 8_100)

        assertEquals(2, calculator.metrics.rebufferCount)
        assertEquals(500L, calculator.metrics.rebufferTimeMs)
        assertEquals("Ready", calculator.metrics.playbackState)
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
    fun `reset starts a fresh measurement`() {
        calculator.onFirstFrameRendered(nowMs = 1_850)
        calculator.onDroppedFrames(5)

        calculator.reset(nowMs = 10_000)

        assertNull(calculator.metrics.startupTimeMs)
        assertEquals(0, calculator.metrics.droppedFrames)
    }
}
