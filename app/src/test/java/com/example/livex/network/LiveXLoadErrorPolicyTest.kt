package com.example.livex.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.SocketTimeoutException

/** DataSpec/LoadEventInfo use android.net.Uri, so this runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
class LiveXLoadErrorPolicyTest {

    private val policy = LiveXLoadErrorPolicy(jitter = { 1.0 })
    private val dataSpec = DataSpec(Uri.parse("https://example.com/seg.ts"))

    private fun info(dataType: Int, error: IOException, errorCount: Int) = LoadErrorInfo(
        LoadEventInfo(LoadEventInfo.getNewId(), dataSpec, 0),
        MediaLoadData(dataType),
        error,
        errorCount,
    )

    private fun http(code: Int, headers: Map<String, List<String>> = emptyMap()) =
        HttpDataSource.InvalidResponseCodeException(code, null, null, headers, dataSpec, ByteArray(0))

    private val timeout = HttpDataSource.HttpDataSourceException(
        SocketTimeoutException(), dataSpec,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        HttpDataSource.HttpDataSourceException.TYPE_READ,
    )

    @Test
    fun `manifests get more attempts than segments`() {
        assertEquals(6, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MANIFEST))
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA_INITIALIZATION))
    }

    @Test
    fun `manifest retries back off exponentially from 500 ms up to 4 s`() {
        val delays = (1..6).map { policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, timeout, it)) }

        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 4_000L, 4_000L), delays)
    }

    @Test
    fun `segment retries back off from 1 s up to 8 s`() {
        val delays = (1..5).map { policy.getRetryDelayMsFor(info(C.DATA_TYPE_MEDIA, http(503), it)) }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L), delays)
    }

    @Test
    fun `jitter spreads retries from many clients`() {
        val jittered = LiveXLoadErrorPolicy(jitter = { 0.8 })

        assertEquals(1_600L, jittered.getRetryDelayMsFor(info(C.DATA_TYPE_MEDIA, timeout, 2)))
    }

    @Test
    fun `auth and client errors are not retried`() {
        listOf(400, 401, 403, 410).forEach { code ->
            assertEquals("HTTP $code", C.TIME_UNSET, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MEDIA, http(code), 1)))
        }
    }

    @Test
    fun `404, 408 and 429 are retried`() {
        listOf(404, 408, 429).forEach { code ->
            assertEquals("HTTP $code", 500L, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, http(code), 1)))
        }
    }

    @Test
    fun `Retry-After from the server is honoured`() {
        val busy = http(503, mapOf("Retry-After" to listOf("6")))

        assertEquals(6_000L, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, busy, 1)))
    }

    @Test
    fun `malformed content is not retried`() {
        val parse = ParserException.createForMalformedManifest("bad", null)

        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, parse, 1)))
    }
}
