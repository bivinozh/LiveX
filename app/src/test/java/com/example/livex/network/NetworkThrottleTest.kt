package com.example.livex.network

import com.example.livex.network.NetworkThrottle.Reservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NetworkThrottleTest {

    private fun throttle(mode: ThrottleMode) = NetworkThrottle(Random(42)).apply { this.mode = mode }

    private fun deliverDelay(reservation: Reservation) = (reservation as Reservation.Deliver).afterMs

    @Test
    fun `unlimited delivers immediately`() {
        assertEquals(Reservation.Deliver(0), throttle(ThrottleMode.UNLIMITED).reserve(1_000_000, nowMs = 0))
    }

    @Test
    fun `800 kbps takes one second for 100 KB`() {
        // 100_000 bytes * 8 = 800_000 bits at 800_000 bit/s
        assertEquals(1_000L, deliverDelay(throttle(ThrottleMode.KBPS_800).reserve(100_000, nowMs = 0)))
    }

    @Test
    fun `rates match their labels`() {
        assertEquals(160L, deliverDelay(throttle(ThrottleMode.MBPS_5).reserve(100_000, nowMs = 0)))
        assertEquals(400L, deliverDelay(throttle(ThrottleMode.MBPS_2).reserve(100_000, nowMs = 0)))
    }

    @Test
    fun `concurrent connections share the link`() {
        val throttle = throttle(ThrottleMode.KBPS_800)

        val video = deliverDelay(throttle.reserve(50_000, nowMs = 0))
        val audio = deliverDelay(throttle.reserve(50_000, nowMs = 0))

        assertEquals(500L, video)
        assertEquals(1_000L, audio) // queued behind the video bytes: total stays at 800 kbps
    }

    @Test
    fun `an idle link does not bank credit for a later burst`() {
        val throttle = throttle(ThrottleMode.KBPS_800)
        throttle.reserve(100_000, nowMs = 0)

        assertEquals(1_000L, deliverDelay(throttle.reserve(100_000, nowMs = 60_000)))
    }

    @Test
    fun `switching mode applies to the next read and drops the old queue`() {
        val throttle = throttle(ThrottleMode.KBPS_800)
        throttle.reserve(1_000_000, nowMs = 0) // 10 s queued at 800 kbps

        throttle.mode = ThrottleMode.UNLIMITED
        assertEquals(Reservation.Deliver(0), throttle.reserve(100_000, nowMs = 100))

        throttle.mode = ThrottleMode.MBPS_2
        assertEquals(400L, deliverDelay(throttle.reserve(100_000, nowMs = 200)))
    }

    @Test
    fun `unstable alternates good periods and outages`() {
        val throttle = throttle(ThrottleMode.UNSTABLE)

        // Probe every 100 ms for 2 minutes with tiny reads (so the queue never builds up).
        val outages = mutableListOf<LongRange>()
        var outageStart: Long? = null
        for (t in 0L..120_000L step 100) {
            val inOutage = throttle.reserve(1, nowMs = t) is Reservation.Wait
            if (inOutage && outageStart == null) outageStart = t
            if (!inOutage && outageStart != null) {
                outages += outageStart until t
                outageStart = null
            }
        }

        assertTrue("expected several drops, got ${outages.size}", outages.size >= 10)
        outages.forEach { range ->
            val length = range.last + 100 - range.first
            assertTrue("outage of $length ms", length in 1_000..3_100)
        }
        outages.zipWithNext().forEach { (a, b) ->
            val goodPeriod = b.first - (a.last + 100)
            assertTrue("good period of $goodPeriod ms", goodPeriod in 2_900..6_100)
        }
    }

    @Test
    fun `an outage tells the reader when to try again`() {
        val throttle = throttle(ThrottleMode.UNSTABLE)
        val firstWait = generateSequence(0L) { it + 100 }
            .map { it to throttle.reserve(1, nowMs = it) }
            .first { it.second is Reservation.Wait }

        val waitMs = (firstWait.second as Reservation.Wait).retryAfterMs
        assertTrue(waitMs in 1..3_000)
    }
}
