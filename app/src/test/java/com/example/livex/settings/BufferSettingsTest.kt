package com.example.livex.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferSettingsTest {

    private val base = BufferSettings(
        minBufferMs = 20_000,
        maxBufferMs = 40_000,
        bufferForPlaybackMs = 1_000,
        bufferForPlaybackAfterRebufferMs = 2_000,
    )

    /** The invariants DefaultLoadControl.Builder enforces (it throws otherwise). */
    private fun assertValidForLoadControl(s: BufferSettings) {
        assertTrue("$s", s.bufferForPlaybackMs >= 0)
        assertTrue("$s", s.bufferForPlaybackAfterRebufferMs >= 0)
        assertTrue("$s", s.minBufferMs >= s.bufferForPlaybackMs)
        assertTrue("$s", s.minBufferMs >= s.bufferForPlaybackAfterRebufferMs)
        assertTrue("$s", s.maxBufferMs >= s.minBufferMs)
    }

    @Test
    fun `default is the fast start preset`() {
        assertEquals(BufferPreset.FAST_START.settings, BufferSettings.DEFAULT)
    }

    @Test
    fun `media3 defaults match the library`() {
        assertEquals(
            BufferSettings(50_000, 50_000, 1_000, 2_000),
            BufferSettings.MEDIA3_DEFAULT,
        )
    }

    @Test
    fun `every preset is valid for DefaultLoadControl`() {
        BufferPreset.entries.forEach { assertValidForLoadControl(it.settings) }
    }

    @Test
    fun `raising min above max pushes max up`() {
        val s = base.with(BufferField.MIN_BUFFER, 60_000)

        assertEquals(60_000, s.minBufferMs)
        assertEquals(60_000, s.maxBufferMs)
        assertValidForLoadControl(s)
    }

    @Test
    fun `lowering min below the start thresholds pulls them down`() {
        val s = base.with(BufferField.MIN_BUFFER, 1_500)

        assertEquals(1_000, s.bufferForPlaybackMs)
        assertEquals(1_500, s.bufferForPlaybackAfterRebufferMs)
        assertValidForLoadControl(s)
    }

    @Test
    fun `lowering max below min pulls min and thresholds down`() {
        val s = base.with(BufferField.MAX_BUFFER, 1_500)

        assertEquals(1_500, s.maxBufferMs)
        assertEquals(1_500, s.minBufferMs)
        assertValidForLoadControl(s)
    }

    @Test
    fun `raising a start threshold above min pushes min (and max) up`() {
        val small = base.copy(minBufferMs = 5_000, maxBufferMs = 8_000)

        val s = small.with(BufferField.PLAYBACK_AFTER_REBUFFER, 12_000)

        assertEquals(12_000, s.bufferForPlaybackAfterRebufferMs)
        assertEquals(12_000, s.minBufferMs)
        assertEquals(12_000, s.maxBufferMs)
        assertValidForLoadControl(s)
    }

    @Test
    fun `values are clamped to the field range`() {
        val s = base.with(BufferField.FOR_PLAYBACK, -500)

        assertEquals(BufferField.FOR_PLAYBACK.range.first, s.bufferForPlaybackMs)
    }

    @Test
    fun `normalized repairs invalid stored values`() {
        val broken = BufferSettings(minBufferMs = 1_000, maxBufferMs = 500, bufferForPlaybackMs = 3_000, bufferForPlaybackAfterRebufferMs = 4_000)

        assertValidForLoadControl(broken.normalized())
    }

    @Test
    fun `matching preset is detected`() {
        assertEquals(BufferPreset.LOW_MEMORY_TV, BufferPreset.matching(BufferPreset.LOW_MEMORY_TV.settings))
        assertNull(BufferPreset.matching(base))
    }

    @Test
    fun `field getters read the right value`() {
        assertEquals(20_000, BufferField.MIN_BUFFER.get(base))
        assertEquals(40_000, BufferField.MAX_BUFFER.get(base))
        assertEquals(1_000, BufferField.FOR_PLAYBACK.get(base))
        assertEquals(2_000, BufferField.PLAYBACK_AFTER_REBUFFER.get(base))
    }
}
