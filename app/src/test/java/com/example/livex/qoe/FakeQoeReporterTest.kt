package com.example.livex.qoe

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeQoeReporterTest {

    private val batches = mutableListOf<List<Map<String, Any?>>>()
    private val reporter = FakeQoeReporter(batchSize = 3, send = { batches += it })

    @Test
    fun `events are sent in batches`() = runTest {
        repeat(4) { reporter.report(QoeEvent.StallStart("s1", it.toLong())) }

        assertEquals(listOf(3), batches.map { it.size })
    }

    @Test
    fun `session end flushes right away`() = runTest {
        reporter.report(QoeEvent.VideoStart("s1", 900, 900))
        reporter.report(QoeEvent.SessionEnd("s1", 5_000, EndReason.EXIT, 4_000, 0, 1_000_000))

        assertEquals(listOf(2), batches.map { it.size })
        assertTrue(batches.single().last()["type"] == "session_end")
    }

    @Test
    fun `payload is flat key values with the event fields`() {
        val payload = FakeQoeReporter.payload(QoeEvent.BitrateSwitch("s1", 7_000, 800_000, 2_500_000, 1280, 720))

        assertEquals(
            mapOf(
                "session_id" to "s1", "type" to "bitrate_switch", "t_ms" to 7_000L,
                "from_bps" to 800_000, "to_bps" to 2_500_000, "height" to 720,
            ),
            payload,
        )
    }
}
