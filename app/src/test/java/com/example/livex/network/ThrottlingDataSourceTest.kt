package com.example.livex.network

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Robolectric for Uri. Time is fake: sleeping just advances [nowMs]. */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class ThrottlingDataSourceTest {

    private var nowMs = 0L
    private val payload = ByteArray(100_000) { it.toByte() }

    private fun readAll(mode: ThrottleMode): ByteArray {
        val throttle = NetworkThrottle(Random(1)).apply { this.mode = mode }
        val source = ThrottlingDataSource(
            upstream = ByteArrayDataSource(payload),
            throttle = throttle,
            clock = { nowMs },
            sleeper = { ms -> nowMs += ms },
        )
        source.open(DataSpec(Uri.parse("https://example.com/segment.ts")))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == -1) break
            out.write(buffer, 0, read)
        }
        source.close()
        return out.toByteArray()
    }

    @Test
    fun `bytes pass through unchanged`() {
        assertArrayEquals(payload, readAll(ThrottleMode.KBPS_800))
    }

    @Test
    fun `800 kbps paces 100 KB over one second`() {
        readAll(ThrottleMode.KBPS_800)

        assertEquals(1_000L, nowMs)
    }

    @Test
    fun `unlimited never sleeps`() {
        readAll(ThrottleMode.UNLIMITED)

        assertEquals(0L, nowMs)
    }
}
