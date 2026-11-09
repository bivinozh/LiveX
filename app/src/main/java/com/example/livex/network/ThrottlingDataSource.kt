package com.example.livex.network

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.InterruptedIOException

/**
 * Wraps an HTTP [DataSource] (OkHttp in this app) and limits how fast bytes reach the player.
 *
 * Reads happen on ExoPlayer's loader threads, so blocking here is fine: it just makes the
 * download take as long as it would on a slow link. Because the bandwidth meter measures
 * transfer start-to-end, the slower delivery lowers the bandwidth estimate, which drives ABR.
 *
 * Reads are capped at [MAX_CHUNK_BYTES] so a mode change takes effect within one small chunk.
 */
@OptIn(UnstableApi::class)
class ThrottlingDataSource(
    private val upstream: DataSource,
    private val throttle: NetworkThrottle,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val sleeper: (Long) -> Unit = ::sleepInterruptibly,
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        awaitLinkUp()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (throttle.mode == ThrottleMode.UNLIMITED) return upstream.read(buffer, offset, length)

        awaitLinkUp()
        val read = upstream.read(buffer, offset, minOf(length, MAX_CHUNK_BYTES))
        if (read == C.RESULT_END_OF_INPUT || read == 0) return read
        // Hold the bytes for as long as they'd take on the simulated wire.
        deliver(read)
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    /** Unstable mode: while the link is down, nothing moves (like a TCP connection stalling). */
    private fun awaitLinkUp() {
        while (true) {
            when (val reservation = throttle.reserve(0, clock())) {
                is NetworkThrottle.Reservation.Wait -> sleeper(reservation.retryAfterMs.coerceIn(1, MAX_SLEEP_MS))
                is NetworkThrottle.Reservation.Deliver -> return
            }
        }
    }

    private fun deliver(bytes: Int) {
        while (true) {
            when (val reservation = throttle.reserve(bytes, clock())) {
                // Dropped mid-transfer: wait for the link to come back, then send these bytes.
                is NetworkThrottle.Reservation.Wait -> sleeper(reservation.retryAfterMs.coerceIn(1, MAX_SLEEP_MS))
                is NetworkThrottle.Reservation.Deliver -> {
                    if (reservation.afterMs > 0) sleeper(reservation.afterMs)
                    return
                }
            }
        }
    }

    /** Uses the same throttle for every source it creates: one simulated link per player. */
    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val throttle: NetworkThrottle,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ThrottlingDataSource(upstreamFactory.createDataSource(), throttle)
    }

    private companion object {
        /** 16 KB: ~160 ms at 800 kbps, so mode switches apply quickly. */
        const val MAX_CHUNK_BYTES = 16 * 1024

        /** Re-check at least this often while the link is down, so switching mode ends a drop quickly. */
        const val MAX_SLEEP_MS = 100L
    }
}

/** ExoPlayer cancels a load by interrupting its loader thread: surface that as an I/O interruption. */
private fun sleepInterruptibly(ms: Long) {
    try {
        Thread.sleep(ms)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException("Throttled read cancelled").apply { initCause(e) }
    }
}
