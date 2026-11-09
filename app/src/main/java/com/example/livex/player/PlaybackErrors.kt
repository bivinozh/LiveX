package com.example.livex.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Turns a [PlaybackException] into a message a viewer can act on.
 *
 * Media3 groups error codes by thousands: 1xxx misc, 2xxx input/output (network, HTTP),
 * 3xxx parsing (manifest/container), 4xxx decoding, 5xxx audio track, 6xxx DRM.
 * The error code says *what failed*; the cause chain has the details (HTTP status, SSL failure).
 */
object PlaybackErrors {

    const val NETWORK = "Can't reach the stream server. Check the internet connection and try again."
    const val TIMEOUT = "The stream server took too long to respond. Try again."
    const val SSL = "Secure connection failed: this device doesn't trust the server's certificate."
    const val HTTP_403 = "Access denied (HTTP 403). The stream may be geo-blocked or need a valid token."
    const val HTTP_404 = "Stream not found (HTTP 404). It may have been moved or taken offline."
    const val CLEARTEXT = "Plain HTTP is blocked on Android. The stream must use an https:// URL."
    const val PARSING = "The stream's manifest or media is malformed and couldn't be read."
    const val DECODER = "This device can't decode the stream's video or audio format."
    const val DRM = "This stream is DRM-protected and couldn't be unlocked on this device."
    const val BEHIND_LIVE_WINDOW = "Playback fell behind the live window."
    const val GENERIC = "Playback failed. Please try again."

    fun message(error: PlaybackException): String {
        val code = error.errorCode
        return when {
            // Checked first: an SSL failure surfaces as a plain "connection failed" code.
            error.hasCause { it is SSLException || it is CertificateException || it is CertPathValidatorException } -> SSL

            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> NETWORK
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> TIMEOUT
            code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpMessage(error)
            code == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> CLEARTEXT
            code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> BEHIND_LIVE_WINDOW

            code in 3000..3999 -> PARSING
            code in 4000..4999 -> DECODER
            code in 6000..6999 -> DRM
            else -> GENERIC
        }
    }

    /** ERROR_CODE_IO_BAD_HTTP_STATUS: the status code is on the InvalidResponseCodeException cause. */
    @OptIn(UnstableApi::class)
    private fun httpMessage(error: PlaybackException): String {
        val status = error.causes()
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
        return when (status) {
            403 -> HTTP_403
            404 -> HTTP_404
            null -> "The stream server returned an error."
            in 500..599 -> "The stream server had a problem (HTTP $status). Try again later."
            else -> "The stream server returned an error (HTTP $status)."
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(cause) { it.cause }

    private fun Throwable.hasCause(predicate: (Throwable) -> Boolean) = causes().any(predicate)
}
