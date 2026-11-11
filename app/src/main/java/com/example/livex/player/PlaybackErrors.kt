package com.example.livex.player

import android.media.MediaCodec
import android.media.MediaDrm
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
    const val DRM_UNSUPPORTED = "This device has no Widevine DRM, so protected streams can't play here."
    const val DRM_PROVISIONING = "This device couldn't be set up for DRM (provisioning failed). Check the network and try again."
    const val DRM_CONTENT = "The protected content couldn't be decrypted (missing or wrong key)."
    const val DRM_LICENSE_DENIED = "The license server didn't grant a license for this video on this device."
    const val DRM_LICENSE_FORBIDDEN = "License refused (HTTP 403): this account or device isn't entitled to play this video."
    const val DRM_SECURITY_LEVEL = "This device's DRM security level isn't allowed for this video (HD/UHD usually needs Widevine L1)."
    const val DRM_SYSTEM = "The device's DRM system reported an error. Restarting the app may help."
    const val DRM_REVOKED = "This device's DRM certificate has been revoked; protected video can't play on it."
    const val DRM_EXPIRED = "The video license has expired. Reopen the video to get a new one."
    const val DRM_DECRYPT_FAILED = "Decryption failed: this device's license doesn't cover this video (HD or L1-only content on a Widevine L3 device)."
    const val DRM_KEY_MISSING = "This quality's decryption key isn't in this device's license (HD keys usually need Widevine L1)."
    const val DRM_OUTPUT_PROTECTION = "The display connection isn't protected enough for this video (HDCP required)."
    const val BEHIND_LIVE_WINDOW = "Playback fell behind the live window."
    const val GENERIC = "Playback failed. Please try again."

    /**
     * @param isProtected the stream is DRM-protected. Decryption happens inside the codec, and when it
     * fails the codec can die before ExoPlayer reads a DRM code from it, leaving only a decoder error.
     */
    fun message(error: PlaybackException, isProtected: Boolean = false): String {
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
            // DRM failures inside the decoder (decrypt step) surface as decoder errors: unwrap them.
            code in 4000..4999 -> drmInsideDecoder(error) ?: if (isProtected) DRM_DECRYPT_FAILED else DECODER
            code in 6000..6999 -> drmMessage(error)
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

    /**
     * Native DRM status codes (frameworks/av MediaErrors.h, DRM_ERROR_BASE = -2000) carried by
     * MediaCodec.CodecException when decryption fails inside the codec. Null = not a DRM code.
     */
    fun drmDecodeMessage(nativeErrorCode: Int): String? = when (nativeErrorCode) {
        -2001 -> DRM_KEY_MISSING // ERROR_DRM_NO_LICENSE: key for this track not loaded
        -2002 -> DRM_EXPIRED // ERROR_DRM_LICENSE_EXPIRED
        -2011 -> DRM_OUTPUT_PROTECTION // ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION
        in -2099..-2003 -> DRM
        else -> null
    }

    private fun drmInsideDecoder(error: PlaybackException): String? = error.causes().firstNotNullOfOrNull { cause ->
        when (cause) {
            is MediaCodec.CodecException -> drmDecodeMessage(cause.errorCode)
            is MediaCodec.CryptoException -> when (cause.errorCode) {
                MediaDrm.ErrorCodes.ERROR_NO_KEY -> DRM_KEY_MISSING
                MediaDrm.ErrorCodes.ERROR_KEY_EXPIRED -> DRM_EXPIRED
                MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_OUTPUT_PROTECTION -> DRM_OUTPUT_PROTECTION
                MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_SECURITY -> DRM_SECURITY_LEVEL
                else -> DRM
            }
            else -> null
        }
    }

    /** DRM (6xxx): which step of the license flow failed decides what the viewer can do about it. */
    @OptIn(UnstableApi::class)
    private fun drmMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> DRM_UNSUPPORTED
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> DRM_PROVISIONING
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> DRM_CONTENT
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> {
            // The license request is plain HTTP: a status code on the cause says why.
            val status = error.causes()
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()
                ?.responseCode
            when (status) {
                null -> DRM_LICENSE_DENIED
                401, 403 -> DRM_LICENSE_FORBIDDEN
                in 500..599 -> "The license server had a problem (HTTP $status). Try again later."
                else -> "$DRM_LICENSE_DENIED (HTTP $status)"
            }
        }
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> DRM_SECURITY_LEVEL
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> DRM_SYSTEM
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> DRM_REVOKED
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> DRM_EXPIRED
        else -> DRM
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(cause) { it.cause }

    private fun Throwable.hasCause(predicate: (Throwable) -> Boolean) = causes().any(predicate)
}
