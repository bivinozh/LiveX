package com.example.livex.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException

/** Robolectric: PlaybackException reads SystemClock in its constructor. */
@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PlaybackErrorsTest {

    private fun error(code: Int, cause: Throwable? = null) = PlaybackException("test", cause, code)

    private fun httpStatus(status: Int) = error(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        HttpDataSource.InvalidResponseCodeException(
            status, null, null, emptyMap(), DataSpec(Uri.parse("https://example.com/a.m3u8")), ByteArray(0),
        ),
    )

    @Test
    fun `connection failure asks to check the internet connection`() {
        val message = PlaybackErrors.message(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        assertEquals(PlaybackErrors.NETWORK, message)
    }

    @Test
    fun `timeout has its own message`() {
        val message = PlaybackErrors.message(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))

        assertEquals(PlaybackErrors.TIMEOUT, message)
    }

    @Test
    fun `untrusted certificate is reported as a secure connection failure, not a generic network error`() {
        val cause = IOException(SSLHandshakeException("handshake").apply {
            initCause(CertPathValidatorException("Trust anchor for certification path not found."))
        })

        val message = PlaybackErrors.message(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, cause))

        assertEquals(PlaybackErrors.SSL, message)
    }

    @Test
    fun `http 404 says the stream was not found`() {
        assertEquals(PlaybackErrors.HTTP_404, PlaybackErrors.message(httpStatus(404)))
    }

    @Test
    fun `http 403 says access is denied`() {
        assertEquals(PlaybackErrors.HTTP_403, PlaybackErrors.message(httpStatus(403)))
    }

    @Test
    fun `server errors mention the status and suggest trying later`() {
        val message = PlaybackErrors.message(httpStatus(503))

        assertTrue(message, message.contains("503"))
    }

    @Test
    fun `other http statuses include the status code`() {
        val message = PlaybackErrors.message(httpStatus(410))

        assertTrue(message, message.contains("410"))
    }

    @Test
    fun `decoder errors explain the format is not supported`() {
        listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        ).forEach { code ->
            assertEquals(PlaybackErrors.DECODER, PlaybackErrors.message(error(code)))
        }
    }

    @Test
    fun `each drm failure gets its own explanation`() {
        mapOf(
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED to PlaybackErrors.DRM_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED to PlaybackErrors.DRM_PROVISIONING,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR to PlaybackErrors.DRM_CONTENT,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED to PlaybackErrors.DRM_LICENSE_DENIED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION to PlaybackErrors.DRM_SECURITY_LEVEL,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR to PlaybackErrors.DRM_SYSTEM,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED to PlaybackErrors.DRM_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED to PlaybackErrors.DRM_EXPIRED,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED to PlaybackErrors.DRM,
        ).forEach { (code, expected) ->
            assertEquals("code $code", expected, PlaybackErrors.message(error(code)))
        }
    }

    @Test
    fun `native drm codes hidden in decoder errors are recognised`() {
        // Seen on the L3 emulator: HD key not in the license -> MediaCodec error -2001 -> DECODING_FAILED.
        assertEquals(PlaybackErrors.DRM_KEY_MISSING, PlaybackErrors.drmDecodeMessage(-2001))
        assertEquals(PlaybackErrors.DRM_EXPIRED, PlaybackErrors.drmDecodeMessage(-2002))
        assertEquals(PlaybackErrors.DRM_OUTPUT_PROTECTION, PlaybackErrors.drmDecodeMessage(-2011))
        assertEquals(PlaybackErrors.DRM, PlaybackErrors.drmDecodeMessage(-2005))
        assertEquals(null, PlaybackErrors.drmDecodeMessage(-1)) // a genuine decoder problem
        assertEquals(null, PlaybackErrors.drmDecodeMessage(0))
    }

    @Test
    fun `a decoder failure on a protected stream is reported as a decryption failure`() {
        // Seen on the L3 emulator with an L1-only license: the codec dies while decrypting and
        // ExoPlayer only reports DECODING_FAILED (IllegalStateException), with no DRM code left.
        val decodingFailed = error(PlaybackException.ERROR_CODE_DECODING_FAILED)

        assertEquals(PlaybackErrors.DRM_DECRYPT_FAILED, PlaybackErrors.message(decodingFailed, isProtected = true))
        assertEquals(PlaybackErrors.DECODER, PlaybackErrors.message(decodingFailed, isProtected = false))
    }

    @Test
    fun `license server http errors say whether access or the server failed`() {
        val forbidden = error(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, httpStatus(403).cause)
        val serverError = error(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, httpStatus(500).cause)

        assertEquals(PlaybackErrors.DRM_LICENSE_FORBIDDEN, PlaybackErrors.message(forbidden))
        assertTrue(PlaybackErrors.message(serverError).contains("500"))
    }

    @Test
    fun `malformed manifest is a parsing error`() {
        val message = PlaybackErrors.message(error(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED))

        assertEquals(PlaybackErrors.PARSING, message)
    }

    @Test
    fun `unknown errors fall back to a generic message`() {
        val message = PlaybackErrors.message(error(PlaybackException.ERROR_CODE_UNSPECIFIED))

        assertEquals(PlaybackErrors.GENERIC, message)
    }
}
