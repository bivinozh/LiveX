package com.example.livex.network

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * A simulated network link shared by every connection of one player.
 *
 * Shared on purpose: DASH loads audio and video segments in parallel (and manifests refresh on
 * the side). On a real link they compete for the same bandwidth, so the limit applies to the
 * total, not per connection.
 *
 * Works like a single queue on a wire: delivering N bytes at R bit/s occupies the link for
 * N*8/R seconds, and each reservation is scheduled after the previous one. Pure Kotlin with time
 * passed in, so it's unit-tested without real sleeping.
 *
 * Thread-safe: reserve() is called from ExoPlayer's loader threads, [mode] from the main thread.
 */
class NetworkThrottle(private val random: Random = Random.Default) {

    sealed interface Reservation {
        /** Hand the bytes to the player after [afterMs] (the time they take on the simulated wire). */
        data class Deliver(val afterMs: Long) : Reservation

        /** The link is down (Unstable mode): nothing gets through; ask again after [retryAfterMs]. */
        data class Wait(val retryAfterMs: Long) : Reservation
    }

    @Volatile
    var mode: ThrottleMode = ThrottleMode.UNLIMITED
        set(value) {
            synchronized(this) {
                field = value
                // Bytes queued under the old mode are forgotten: the new speed applies immediately.
                linkFreeAtMs = Double.NEGATIVE_INFINITY
                phaseEndsAtMs = Long.MIN_VALUE
            }
        }

    /** When the simulated wire finishes sending everything reserved so far. */
    private var linkFreeAtMs = Double.NEGATIVE_INFINITY

    // Unstable mode schedule.
    private var phaseEndsAtMs = Long.MIN_VALUE
    private var phaseBitsPerSecond = 0L

    @Synchronized
    fun reserve(bytes: Int, nowMs: Long): Reservation {
        val bitsPerSecond = when (val current = mode) {
            ThrottleMode.UNLIMITED -> return Reservation.Deliver(0)
            ThrottleMode.UNSTABLE -> unstableRate(nowMs)
            else -> current.bitsPerSecond!!
        }
        if (bitsPerSecond == 0L) return Reservation.Wait(phaseEndsAtMs - nowMs)

        // Continue right after the previous reservation if the link is still (nearly) busy;
        // otherwise it was idle and starts now. Idle time is NOT banked as credit for a later burst.
        val start = if (linkFreeAtMs + IDLE_TOLERANCE_MS >= nowMs) linkFreeAtMs else nowMs.toDouble()
        linkFreeAtMs = start + bytes * 8_000.0 / bitsPerSecond
        return Reservation.Deliver((linkFreeAtMs - nowMs).roundToLong().coerceAtLeast(0))
    }

    /** Alternates good periods and complete drops, with random lengths and speeds. */
    private fun unstableRate(nowMs: Long): Long {
        while (nowMs >= phaseEndsAtMs) {
            val startsAt = if (phaseEndsAtMs == Long.MIN_VALUE) nowMs else phaseEndsAtMs
            val wasGood = phaseBitsPerSecond > 0 && phaseEndsAtMs != Long.MIN_VALUE
            if (wasGood) {
                phaseBitsPerSecond = 0
                phaseEndsAtMs = startsAt + random.nextLong(1_000, 3_001)
            } else {
                phaseBitsPerSecond = random.nextLong(1_500_000, 6_000_001)
                phaseEndsAtMs = startsAt + random.nextLong(3_000, 6_001)
            }
        }
        return phaseBitsPerSecond
    }

    private companion object {
        /** Gaps shorter than this (rounding, thread scheduling) still count as a busy link. */
        const val IDLE_TOLERANCE_MS = 50.0
    }
}
