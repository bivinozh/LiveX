package com.example.livex.network

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * How ExoPlayer reacts when a manifest/playlist or segment request fails.
 *
 * Two questions, answered per failure:
 * - [getRetryDelayMsFor]: retry after how long? C.TIME_UNSET = don't retry, fail now.
 * - [getMinimumLoadableRetryCount]: how many failures before the error is surfaced to the player
 *   (onPlayerError)? Until then failures are silent (we see them as onLoadError / load_error).
 *
 * Manifests vs segments:
 * - A manifest (or live playlist) failure blocks everything: no manifest, nothing to play, and a
 *   live playlist that can't refresh means the buffer drains. Retry more (6), start fast (500 ms).
 * - A segment failure has a buffer behind it and alternatives: before retrying, ExoPlayer may
 *   exclude that rendition and switch to another (getFallbackSelectionFor, kept from the default).
 *   Fewer attempts (3), longer waits (1 s -> 8 s): hammering a struggling CDN makes it worse.
 *
 * Backoff is exponential with jitter: 1x, 2x, 4x... capped, times a random 0.8-1.2. Without jitter,
 * thousands of players that failed together (a CDN blip during a live event) all retry at the same
 * instant and knock the origin over again: a "thundering herd".
 */
@OptIn(UnstableApi::class)
class LiveXLoadErrorPolicy(
    private val jitter: () -> Double = { Random.nextDouble(1 - JITTER, 1 + JITTER) },
) : DefaultLoadErrorHandlingPolicy() {

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = when (dataType) {
        C.DATA_TYPE_MANIFEST -> MANIFEST_RETRIES
        C.DATA_TYPE_MEDIA, C.DATA_TYPE_MEDIA_INITIALIZATION -> SEGMENT_RETRIES
        else -> super.getMinimumLoadableRetryCount(dataType)
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        // The default already refuses to retry what can't succeed: malformed content
        // (ParserException), file not found, cleartext not permitted...
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET

        val http = loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException
        if (http != null && http.responseCode in NOT_RETRIABLE) return C.TIME_UNSET

        val isManifest = loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MANIFEST
        val baseMs = if (isManifest) MANIFEST_BASE_MS else SEGMENT_BASE_MS
        val maxMs = if (isManifest) MANIFEST_MAX_MS else SEGMENT_MAX_MS
        val attempt = loadErrorInfo.errorCount.coerceIn(1, 16)
        val backoffMs = (min(baseMs shl (attempt - 1), maxMs) * jitter()).roundToLong()

        // 429/503 may say when to come back; asking earlier would just fail again.
        val retryAfterMs = http?.headerFields?.entries
            ?.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
            ?.value?.firstOrNull()?.trim()?.toLongOrNull()
            ?.let { it * 1000 }
            ?.coerceAtMost(MAX_RETRY_AFTER_MS)
        return maxOf(backoffMs, retryAfterMs ?: 0)
    }

    companion object {
        const val MANIFEST_RETRIES = 6
        const val SEGMENT_RETRIES = 3
        const val MANIFEST_BASE_MS = 500L
        const val MANIFEST_MAX_MS = 4_000L
        const val SEGMENT_BASE_MS = 1_000L
        const val SEGMENT_MAX_MS = 8_000L
        const val MAX_RETRY_AFTER_MS = 30_000L
        const val JITTER = 0.2

        /**
         * The request itself is wrong or not allowed: retrying gives the same answer.
         * Not here: 404 (live edge segment not on this CDN node yet), 408 (timeout), 429 (rate limit).
         */
        val NOT_RETRIABLE = setOf(400, 401, 403, 405, 410, 451)
    }
}
