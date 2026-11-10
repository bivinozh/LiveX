package com.example.livex.player.ads

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.AdOverlayInfo
import androidx.media3.common.AdViewProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.Ad
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Client-side ad insertion (CSAI) with the Google IMA SDK.
 *
 * - [adsLoader]: one ImaAdsLoader per engine. It requests the VAST/VMAP tag, tells the player where
 *   the ad breaks are (AdPlaybackState) and reports impressions/quartiles back to the ad server.
 *   It supports one player at a time: [setPlayer] before preparing, setPlayer(null) before release.
 * - [adViewProvider]: IMA draws its ad UI (and measures viewability) inside the PlayerView's ad
 *   overlay. The PlayerView lives in Compose and can be recreated, so it's attached/detached here.
 */
@OptIn(UnstableApi::class)
class ImaAdsController(private val context: Context) {

    private val log = AdEventLog()
    private val _events = MutableStateFlow<List<AdEventLog.Entry>>(emptyList())
    val events: StateFlow<List<AdEventLog.Entry>> = _events.asStateFlow()

    val adViewProvider = AdViewHolder()

    val adsLoader: ImaAdsLoader = ImaAdsLoader.Builder(context)
        .setAdEventListener(::onAdEvent)
        .setAdErrorListener(::onAdError)
        .build()

    fun setPlayer(player: Player?) = adsLoader.setPlayer(player)

    // --- Server-side ad insertion (IMA DAI) ---

    private var serverSideLoader: ImaServerSideAdInsertionMediaSource.AdsLoader? = null

    /** Which DAI breaks were already played etc., carried over when the same stream is rebuilt. */
    private var serverSideState: ImaServerSideAdInsertionMediaSource.AdsLoader.State? = null

    /**
     * One SSAI AdsLoader per player (it binds to a single player). [keepState] = rebuilding the same
     * stream: resume the DAI session state so watched breaks stay watched.
     */
    fun newServerSideLoader(keepState: Boolean): ImaServerSideAdInsertionMediaSource.AdsLoader {
        if (!keepState) serverSideState = null
        return ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(context, adViewProvider)
            .setAdEventListener(::onAdEvent)
            .setAdErrorListener(::onAdError)
            .apply { serverSideState?.let { setAdsLoaderState(it) } }
            .build()
            .also { serverSideLoader = it }
    }

    fun setServerSidePlayer(player: Player) {
        serverSideLoader?.setPlayer(player)
    }

    /** Call after the player is released; keeps the state for a rebuild of the same stream. */
    fun releaseServerSideLoader() {
        serverSideLoader?.let { serverSideState = it.release() }
        serverSideLoader = null
    }

    fun clearEvents() {
        log.clear()
        _events.value = emptyList()
    }

    /** Engine release: detach from the player first, then free the IMA SDK (and drop the view). */
    fun release() {
        adsLoader.setPlayer(null)
        adsLoader.release()
        releaseServerSideLoader()
        serverSideState = null
        adViewProvider.playerView = null
    }

    private fun onAdEvent(event: AdEvent) {
        if (event.type in NOISY_EVENTS) return
        val detail = when (event.type) {
            // LOG carries non-fatal problems, e.g. one VMAP break failed while others are fine.
            AdEvent.AdEventType.LOG -> event.adData.entries.joinToString { "${it.key}=${it.value}" }
            else -> describe(event.ad)
        }
        record(event.type.name, detail, isError = event.type == AdEvent.AdEventType.LOG)
    }

    /**
     * An ad (or the whole tag) failed. ImaAdsLoader marks the ad group as failed in the
     * AdPlaybackState, so the player skips it and the content continues: ad errors are never fatal.
     */
    private fun onAdError(event: AdErrorEvent) {
        val error = event.error
        // errorCode.toString() is "AdErrorCode [name: …, number: …]": use the enum name instead.
        val codeName = error.errorCode.name
        val codeNumber = error.errorCodeNumber
        record(
            name = "ERROR",
            detail = AdErrorText.overlay(codeName, codeNumber),
            isError = true,
            logcatText = "ERROR " + AdErrorText.logcat(codeName, codeNumber, error.message),
        )
    }

    private fun record(name: String, detail: String?, isError: Boolean, logcatText: String? = null) {
        val entry = log.add(name, detail, isError)
        val line = logcatText ?: entry.text
        if (isError) Log.w(TAG, line) else Log.i(TAG, line)
        _events.value = log.entries
    }

    /** "pre-roll · ad 1/1 · 10 s" from the IMA pod info (podIndex 0 = pre, -1 = post). */
    private fun describe(ad: Ad?): String? {
        ad ?: return null
        val pod = ad.adPodInfo
        val kind = when (pod.podIndex) {
            0 -> "pre-roll"
            -1 -> "post-roll"
            else -> "mid-roll ${pod.podIndex}"
        }
        return "$kind · ad ${pod.adPosition}/${pod.totalAds} · ${ad.duration.roundToInt()} s"
    }

    companion object {
        const val TAG = "LiveX-Ads"

        /** Fired several times per second: would flood the log. */
        private val NOISY_EVENTS = setOf(AdEvent.AdEventType.AD_PROGRESS, AdEvent.AdEventType.AD_BUFFERING)
    }
}

/** AdViewProvider backed by whichever PlayerView is currently on screen. */
@OptIn(UnstableApi::class)
class AdViewHolder : AdViewProvider {
    /** Set by the screen when the PlayerView is created, cleared when it's released (no Activity leak). */
    var playerView: PlayerView? = null

    override fun getAdViewGroup(): ViewGroup =
        checkNotNull(playerView?.adViewGroup) { "Ads requested before a PlayerView was attached (setAdView)" }

    override fun getAdOverlayInfos(): List<AdOverlayInfo> = playerView?.adOverlayInfos ?: emptyList()
}
