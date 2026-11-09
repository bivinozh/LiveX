package com.example.livex.player.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric: MediaItem classes may touch Android utilities. */
@RunWith(AndroidJUnit4::class)
class LiveLatencyTest {

    @Test
    fun `three targets of 3, 10 and 30 seconds`() {
        assertEquals(listOf(3_000L, 10_000L, 30_000L), LiveLatency.entries.map { it.targetOffsetMs })
        assertEquals(listOf("3 s", "10 s", "30 s"), LiveLatency.entries.map { it.label })
    }

    @Test
    fun `default is 10 seconds`() {
        assertEquals(LiveLatency.STANDARD, LiveLatency.DEFAULT)
    }

    @Test
    fun `maps to a MediaItem LiveConfiguration`() {
        val config = LiveLatency.LOW.liveConfiguration()

        assertEquals(3_000L, config.targetOffsetMs)
        assertEquals(LiveLatency.LOW.minPlaybackSpeed, config.minPlaybackSpeed)
        assertEquals(LiveLatency.LOW.maxPlaybackSpeed, config.maxPlaybackSpeed)
    }

    @Test
    fun `speed range stays close to 1x so the adjustment is invisible`() {
        LiveLatency.entries.forEach {
            assertTrue(it.minPlaybackSpeed in 0.9f..1f)
            assertTrue(it.maxPlaybackSpeed in 1f..1.1f)
        }
    }
}
