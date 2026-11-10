package com.example.livex.player.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdEventLogTest {

    @Test
    fun `entries keep name and detail`() {
        val log = AdEventLog()

        val entry = log.add("STARTED", "pre-roll · ad 1/1 · 10 s")

        assertEquals("STARTED · pre-roll · ad 1/1 · 10 s", entry.text)
        assertEquals(listOf(entry), log.entries)
    }

    @Test
    fun `detail is optional`() {
        assertEquals("ALL_ADS_COMPLETED", AdEventLog().add("ALL_ADS_COMPLETED", null).text)
    }

    @Test
    fun `errors are flagged`() {
        val log = AdEventLog()

        assertTrue(log.add("ERROR", "VAST_EMPTY_RESPONSE (1009)", isError = true).isError)
    }

    @Test
    fun `keeps the most recent entries, oldest first`() {
        val log = AdEventLog(maxEntries = 3)

        listOf("LOADED", "STARTED", "FIRST_QUARTILE", "MIDPOINT", "THIRD_QUARTILE").forEach { log.add(it, null) }

        assertEquals(listOf("FIRST_QUARTILE", "MIDPOINT", "THIRD_QUARTILE"), log.entries.map { it.text })
    }

    @Test
    fun `clear forgets everything`() {
        val log = AdEventLog()
        log.add("LOADED", null)

        log.clear()

        assertTrue(log.entries.isEmpty())
    }
}
