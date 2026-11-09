package com.example.livex.qoe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferHistoryTest {

    @Test
    fun `keeps samples in order`() {
        val history = BufferHistory(windowMs = 60_000)

        history.add(BufferPoint(atMs = 0, bufferedMs = 1_000, isStalled = false))
        history.add(BufferPoint(atMs = 500, bufferedMs = 2_000, isStalled = false))

        assertEquals(listOf(1_000L, 2_000L), history.points.map { it.bufferedMs })
    }

    @Test
    fun `drops samples older than the window`() {
        val history = BufferHistory(windowMs = 60_000)

        (0..130).forEach { i -> history.add(BufferPoint(atMs = i * 500L, bufferedMs = i.toLong(), isStalled = false)) }

        val points = history.points
        val newest = points.last().atMs
        assertTrue(points.all { newest - it.atMs <= 60_000 })
        assertEquals(65_000L, newest)
        assertEquals(5_000L, points.first().atMs)
    }

    @Test
    fun `clear empties the history`() {
        val history = BufferHistory(windowMs = 60_000)
        history.add(BufferPoint(atMs = 0, bufferedMs = 1_000, isStalled = false))

        history.clear()

        assertTrue(history.points.isEmpty())
    }
}
