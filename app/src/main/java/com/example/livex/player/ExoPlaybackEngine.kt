package com.example.livex.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import com.example.livex.data.Stream
import com.example.livex.qoe.EndReason
import com.example.livex.qoe.QoeEventBus
import com.example.livex.qoe.QoeEventLogger
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.QoeTracker
import com.example.livex.network.LiveXLoadErrorPolicy
import androidx.media3.common.PlaybackException
import com.example.livex.network.NetworkThrottle
import androidx.media3.ui.PlayerView
import com.example.livex.data.DaiStream
import com.example.livex.player.ads.AdBreak
import com.example.livex.player.ads.AdBreakKind
import com.example.livex.player.ads.AdSeekPolicy
import com.example.livex.player.ads.SeekOutcome
import com.example.livex.player.ads.VodProgress
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder
import com.example.livex.player.ads.AdState
import com.example.livex.player.drm.DrmStatus
import com.example.livex.player.drm.WidevineInfo
import com.example.livex.player.drm.WidevineInfoReader
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import com.example.livex.player.ads.ImaAdsController
import com.example.livex.player.live.LiveWindow
import com.example.livex.network.ThrottleMode
import com.example.livex.network.ThrottlingDataSource
import com.example.livex.qoe.BufferHistory
import com.example.livex.qoe.BufferPoint
import com.example.livex.qoe.PlayerSample
import com.example.livex.qoe.StatsState
import com.example.livex.di.UserAgent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** How often the stats overlay (and buffered duration) is refreshed. */
private val STATS_INTERVAL = 500.milliseconds

/** ExoPlayer-backed [PlaybackEngine]; one instance per PlayerViewModel (ViewModel-scoped in Hilt). */
@OptIn(UnstableApi::class)
class ExoPlaybackEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    /** Shared with the manifest viewer (see NetworkModule.provideUserAgent). */
    @param:UserAgent private val userAgent: String,
    qoeEventBus: QoeEventBus,
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Structured QoE events (session, VST, stalls, switches, ads, errors, EBVS) onto the app-wide bus. */
    private val qoeEvents = QoeEventLogger(emit = qoeEventBus::emit)
    private val tracker = QoeTracker(onUpdate = { qoeEvents.onMetrics(it, SystemClock.elapsedRealtime()) })

    private var currentStream: Stream? = null
    private var resumeOnStart = false
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()
    override val metrics: StateFlow<QoeMetrics> = tracker.metrics
    private val _stats = MutableStateFlow(StatsState())
    private val bufferHistory = BufferHistory()
    private val _live = MutableStateFlow<LiveWindow?>(null)
    override val live: StateFlow<LiveWindow?> = _live.asStateFlow()
    private val window = Timeline.Window()
    private val period = Timeline.Period()

    // Client-side ads (IMA). One loader for the engine's lifetime; attached to each new player.
    private val ads = ImaAdsController(context) { name, detail, isError ->
        qoeEvents.onAdEvent(name, detail, isError, SystemClock.elapsedRealtime())
    }
    private val _ad = MutableStateFlow(AdState())
    override val ad: StateFlow<AdState> = _ad.asStateFlow()
    private val _progress = MutableStateFlow<VodProgress?>(null)
    private val _widevine = MutableStateFlow<WidevineInfo?>(null)
    override val widevine: StateFlow<WidevineInfo?> = _widevine.asStateFlow()
    override val progress: StateFlow<VodProgress?> = _progress.asStateFlow()

    /** Snap-back: where to continue once the ad break we snapped to has played. */
    private var resumeAfterAdMs: Long? = null

    private val snapBackListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val adEnded = oldPosition.adGroupIndex != C.INDEX_UNSET && newPosition.adGroupIndex == C.INDEX_UNSET
            val target = resumeAfterAdMs ?: return
            if (!adEnded) return
            resumeAfterAdMs = null
            val player = _player.value ?: return
            // ExoPlayer may already resume at the requested position; only seek if it didn't.
            if (kotlin.math.abs(player.contentPosition - target) > 1_000) player.seekTo(target)
        }
    }

    /** Fatal errors and the natural end of a session, for the QoE event log. */
    private val sessionListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val message = PlaybackErrors.message(error, isProtected = currentStream?.drm != null)
            qoeEvents.onPlayerError(error.errorCodeName, message, SystemClock.elapsedRealtime())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                qoeEvents.endSession(SystemClock.elapsedRealtime(), EndReason.COMPLETED)
            }
        }
    }

    /** One simulated link for the engine's lifetime: survives player rebuilds, switchable at runtime. */
    private val throttle = NetworkThrottle()

    private val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    override val stats: StateFlow<StatsState> = _stats.asStateFlow()
    /** Content position: during an ad, currentPosition is the position INSIDE the ad. */
    override val currentPositionMs: Long
        get() = _player.value?.contentPosition ?: 0L

    init {
        // Device DRM capabilities don't change: read once. MediaDrm talks to the DRM HAL over binder.
        scope.launch(Dispatchers.IO) { _widevine.value = WidevineInfoReader.read() }

        // Buffer, live offset and speed change continuously without callbacks, so sample them.
        // Runs on the main thread: Player getters must be called on the player's application looper.
        scope.launch {
            while (isActive) {
                val current = _player.value
                if (current != null) {
                    val nowMs = SystemClock.elapsedRealtime()
                    val sample = current.sample()
                    tracker.onTick(sample.bufferedDurationMs, nowMs)
                    val metrics = tracker.metrics.value
                    bufferHistory.add(BufferPoint(nowMs, sample.bufferedDurationMs, metrics.stalls.isStalled))
                    _stats.value = StatsState.from(
                        metrics, sample, bufferHistory.points, ads.events.value,
                        drm = DrmStatus(isProtected = currentStream?.drm != null, device = _widevine.value),
                    )
                    _live.value = current.liveWindow()
                    _ad.value = sample.ad
                    _progress.value = current.vodProgress()
                } else {
                    _stats.value = StatsState()
                    _live.value = null
                    _ad.value = AdState()
                    _progress.value = null
                }
                delay(STATS_INTERVAL)
            }
        }
    }

    private fun Player.sample() = PlayerSample(
        bufferedDurationMs = totalBufferedDuration,
        // currentLiveOffset needs a wall-clock-aware live window (HLS EXT-X-PROGRAM-DATE-TIME,
        // DASH availabilityStartTime); otherwise it's TIME_UNSET.
        liveOffsetMs = currentLiveOffset.takeIf { isCurrentMediaItemLive && it != C.TIME_UNSET },
        playbackSpeed = playbackParameters.speed,
        targetOffsetMs = currentWindowOrNull()?.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET },
        ad = adState(),
    )

    /**
     * Which ad of which break is playing. The ad schedule lives in the period's AdPlaybackState
     * (filled in by ImaAdsLoader from the VAST/VMAP response).
     */
    private fun Player.adState(): AdState {
        if (!isPlayingAd || currentTimeline.isEmpty) return AdState()
        currentTimeline.getPeriod(currentPeriodIndex, period)
        val group = currentAdGroupIndex
        return AdState(
            isPlayingAd = true,
            breakKind = AdBreakKind.fromAdGroupTimeUs(period.getAdGroupTimeUs(group)),
            adIndexInBreak = currentAdIndexInAdGroup,
            adsInBreak = period.getAdCountInAdGroup(group),
            remainingMs = if (duration == C.TIME_UNSET) null else (duration - currentPosition).coerceAtLeast(0),
        )
    }

    /**
     * Ad breaks from the AdPlaybackState of every period in the current window, as content positions.
     * Same source for client-side (ImaAdsLoader/VMAP) and server-side (DAI) ads.
     */
    private fun Player.adBreaks(): List<AdBreak> {
        if (currentTimeline.isEmpty) return emptyList()
        val w = currentTimeline.getWindow(currentMediaItemIndex, window)
        return (w.firstPeriodIndex..w.lastPeriodIndex).flatMap { periodIndex ->
            currentTimeline.getPeriod(periodIndex, period)
            val offsetMs = period.positionInWindowMs
            (period.removedAdGroupCount until period.adGroupCount).map { group ->
                val timeUs = period.getAdGroupTimeUs(group)
                AdBreak(
                    timeMs = if (timeUs == C.TIME_END_OF_SOURCE) AdBreak.POST_ROLL else offsetMs + timeUs / 1000,
                    isPlayed = period.hasPlayedAdGroup(group),
                )
            }
        }
    }

    private fun Player.vodProgress(): VodProgress? {
        if (isCurrentMediaItemLive || currentTimeline.isEmpty) return null
        val durationMs = contentDuration.takeIf { it != C.TIME_UNSET } ?: 0L
        return VodProgress(positionMs = contentPosition, durationMs = durationMs, adBreaks = adBreaks())
    }

    /**
     * imadai URI for a DAI stream: live = asset key, VOD = content source + video id. The format tells
     * the SSAI media source whether DAI should return HLS or DASH; adsId keys the ad state.
     */
    private fun daiUri(stream: Stream, dai: DaiStream): Uri =
        ImaServerSideAdInsertionUriBuilder()
            .apply {
                when (dai) {
                    is DaiStream.Live -> setAssetKey(dai.assetKey)
                    is DaiStream.Vod -> setContentSourceId(dai.contentSourceId).setVideoId(dai.videoId)
                }
            }
            .setFormat(if (stream.mimeType == MimeTypes.APPLICATION_MPD) C.CONTENT_TYPE_DASH else C.CONTENT_TYPE_HLS)
            .setAdsId(stream.id)
            .build()

    /**
     * Protected stream on a non-L1 device: don't let ABR select renditions whose keys the license
     * won't include (HD on L3), otherwise playback dies with "requested key has not been loaded"
     * the moment ABR switches up. The device level is read now if the background read hasn't finished.
     */
    private fun capProtectedVideo(player: ExoPlayer) {
        val info = _widevine.value ?: WidevineInfoReader.read().also { _widevine.value = it }
        val maxHeight = info.maxProtectedVideoHeight ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
            .build()
    }

    override fun setAdView(view: PlayerView?) {
        ads.adViewProvider.playerView = view
    }

    /** The current live window, or null for VOD / before the first manifest is parsed. */
    private fun Player.currentWindowOrNull(): Timeline.Window? {
        if (!isCurrentMediaItemLive || currentTimeline.isEmpty) return null
        return currentTimeline.getWindow(currentMediaItemIndex, window)
    }

    /**
     * DVR window for the seek bar. window.liveConfiguration holds the RESOLVED target: our
     * MediaItem.LiveConfiguration merged with what the manifest asks for (and clamped to it).
     */
    private fun Player.liveWindow(): LiveWindow? {
        val w = currentWindowOrNull() ?: return null
        if (w.durationMs == C.TIME_UNSET) return null
        return LiveWindow(
            windowDurationMs = w.durationMs,
            positionMs = currentPosition,
            defaultPositionMs = w.defaultPositionMs,
            targetOffsetMs = w.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET },
        )
    }

    override fun start(stream: Stream, config: PlayerConfig, startPositionMs: Long?) {
        // An explicit position wins (restore after process death). Otherwise resume only when
        // rebuilding the same VOD (optimization switch); a new stream starts from the beginning,
        // and live always joins at the default live position.
        val sameStream = stream == currentStream
        val resumePositionMs = startPositionMs ?: _player.value?.contentPosition
            ?.takeIf { sameStream && !stream.isLive }
        currentStream = stream
        releasePlayer()
        resumeAfterAdMs = null

        val newPlayer = buildPlayer(config, keepServerSideAdState = sameStream)
        // IMA must know the player BEFORE it is prepared (it listens for the timeline and positions).
        ads.setPlayer(newPlayer)
        ads.setServerSidePlayer(newPlayer)
        ads.clearEvents()
        newPlayer.addListener(snapBackListener)
        newPlayer.addListener(sessionListener)
        if (stream.drm != null) capProtectedVideo(newPlayer)
        newPlayer.addAnalyticsListener(tracker)
        // Debug builds: log every load, format switch and state change to logcat (tag "EventLogger").
        // The quickest way to see what ABR downloads (downstreamFormat) vs. what plays (videoInputFormat).
        if (isDebuggable) newPlayer.addAnalyticsListener(EventLogger())
        // The play request: startup (VST) is measured from here to the first rendered frame.
        // A new session per play request; a rebuild ends the previous one as RESTARTED.
        val playRequestAtMs = SystemClock.elapsedRealtime()
        qoeEvents.startSession(stream, playRequestAtMs, System.currentTimeMillis())
        tracker.reset(nowMs = playRequestAtMs)
        bufferHistory.clear()

        val mediaItem = MediaItem.Builder()
            .apply {
                val dai = stream.dai
                if (dai != null) {
                    // Server-side ads: an "imadai" URI. DefaultMediaSourceFactory hands it to the
                    // ImaServerSideAdInsertionMediaSource, which asks DAI for a stitched stream URL.
                    setUri(daiUri(stream, dai))
                } else {
                    setUri(stream.url).setMimeType(stream.mimeType)
                }
            }
            .apply {
                // Live latency control: target offset + the speed range LivePlaybackSpeedControl may
                // use to hold it. Overrides the manifest's own suggestion (e.g. DASH ServiceDescription).
                if (stream.isLive) setLiveConfiguration(config.liveLatency.liveConfiguration())
                // Client-side ads: DefaultMediaSourceFactory sees the AdsConfiguration and wraps the
                // content in an AdsMediaSource driven by ImaAdsLoader.
                stream.adTagUri?.let { setAdsConfiguration(MediaItem.AdsConfiguration.Builder(Uri.parse(it)).build()) }
                // Widevine: the DASH source sees the PSSH in the MPD; the DRM session posts it as a
                // license request to this URL and installs the returned keys in the CDM.
                stream.drm?.let { license ->
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(license.licenseUrl)
                            .build(),
                    )
                }
            }
            .build()
        if (resumePositionMs != null) {
            newPlayer.setMediaItem(mediaItem, resumePositionMs)
        } else {
            newPlayer.setMediaItem(mediaItem)
        }
        newPlayer.prepare()
        newPlayer.playWhenReady = true

        _player.value = newPlayer
    }

    override fun pause() {
        val current = _player.value ?: return
        resumeOnStart = current.playWhenReady
        // pause() == playWhenReady = false: decoders, buffer and the media session stay allocated,
        // so resuming is instant. Loading continues until the buffer target is reached.
        current.pause()
    }

    override fun resume() {
        if (resumeOnStart) _player.value?.play()
        resumeOnStart = false
    }

    override fun goLive() {
        // Default position of a live window = live edge minus the target offset. Unlike a DVR seek,
        // this also clears the speed control's "user chose this offset" override.
        _player.value?.seekToDefaultPosition()
    }

    override fun setThrottleMode(mode: ThrottleMode) {
        throttle.mode = mode
    }

    override fun seekBy(offsetMs: Long): SeekOutcome {
        val current = _player.value ?: return SeekOutcome.Seeked(0)
        if (current.isPlayingAd) return SeekOutcome.BlockedDuringAd
        val fromMs = current.contentPosition
        // Clamp to the start; seeking past the end/live edge is clamped by the player itself.
        val toMs = (fromMs + offsetMs).coerceAtLeast(0)
        val outcome = AdSeekPolicy.resolve(fromMs, toMs, current.adBreaks())
        when (outcome) {
            is SeekOutcome.Seeked -> current.seekTo(outcome.toMs)
            is SeekOutcome.SnappedToAd -> {
                // Seeking to the break's position makes the player play that ad group first.
                resumeAfterAdMs = outcome.resumeAtMs
                current.seekTo(outcome.adTimeMs)
            }
            SeekOutcome.BlockedDuringAd -> Unit
        }
        return outcome
    }

    override fun retry() {
        // After an error the player is IDLE. prepare() reloads from the current position;
        // playWhenReady is unchanged, so it plays again as soon as it's READY.
        _player.value?.prepare()
    }

    override fun rejoinLiveEdge() {
        val current = _player.value ?: return
        // The default position of a live window is its live edge (minus the target live offset).
        current.seekToDefaultPosition()
        current.prepare()
    }

    override fun release() {
        scope.cancel()
        // The viewer left (Back). Before the first frame, this is an EBVS.
        qoeEvents.endSession(SystemClock.elapsedRealtime(), EndReason.EXIT)
        releasePlayer()
        // After the player: setPlayer(null) + release() frees the IMA SDK and its ad display container.
        ads.release()
    }

    private fun releasePlayer() {
        _player.value?.let {
            // Detach IMA first: it must not keep a released player (and setPlayer only allows one).
            ads.setPlayer(null)
            it.removeAnalyticsListener(tracker)
            it.removeListener(snapBackListener)
            it.removeListener(sessionListener)
            it.release()
        }
        _player.value = null
        // After the player (as in the Media3 demo); keeps the DAI state for a same-stream rebuild.
        ads.releaseServerSideLoader()
    }

    private fun buildPlayer(config: PlayerConfig, keepServerSideAdState: Boolean): ExoPlayer {
        // Topic: Startup & buffer tuning. LoadControl is fixed at build time, so new buffer settings
        // mean a new player (start() keeps the VOD position).
        val buffer = config.effectiveBuffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                buffer.minBufferMs,
                buffer.maxBufferMs,
                buffer.bufferForPlaybackMs,
                buffer.bufferForPlaybackAfterRebufferMs,
            )
            .build()

        // Topic: Network. Every HTTP request (playlists, manifests, segments) goes through this factory.
        val httpFactory: DataSource.Factory =
            if (config.okHttpNetwork) {
                OkHttpDataSource.Factory(okHttpClient).setUserAgent(userAgent)
            } else {
                DefaultHttpDataSource.Factory().setUserAgent(userAgent)
            }
        // Network throttle simulator: sits between the player and the HTTP stack. It reads the
        // current mode on every read, so switching modes needs no player rebuild.
        val throttledFactory = ThrottlingDataSource.Factory(httpFactory, throttle)
        // DefaultDataSource adds support for non-HTTP URIs (file://, asset://, content://) on top.
        val dataSourceFactory = DefaultDataSource.Factory(context, throttledFactory)

        // DefaultMediaSourceFactory picks HlsMediaSource / DashMediaSource / ProgressiveMediaSource
        // per MediaItem (from its MIME type, else the URI extension); all of them load through our factory.
        // DRM license requests: through the same OkHttp client and User-Agent (not throttled).
        val drmSessionManagerProvider = DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(httpFactory) }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setDrmSessionManagerProvider(drmSessionManagerProvider)
            // Retries + backoff for failed manifest/segment requests (manifests get more, faster retries).
            .setLoadErrorHandlingPolicy(LiveXLoadErrorPolicy())
            // Client-side ads: MediaItems with an AdsConfiguration get an AdsMediaSource that asks
            // the ImaAdsLoader for ads and draws ad UI in the PlayerView (AdViewProvider).
            // Ad MEDIA (the MP4s from the VAST) loads through the same data source; the VAST/VMAP
            // requests themselves are made by the IMA SDK.
            .setLocalAdInsertionComponents({ ads.adsLoader }, ads.adViewProvider)
        // Server-side ads (DAI): imadai:// MediaItems go to ImaServerSideAdInsertionMediaSource,
        // which loads the stitched content through this same factory (so OkHttp + throttle apply).
        mediaSourceFactory.setServerSideAdInsertionMediaSourceFactory(
            ImaServerSideAdInsertionMediaSource.Factory(ads.newServerSideLoader(keepServerSideAdState), mediaSourceFactory),
        )

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Topic: ABR
        if (config.dataSaverAbr) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(AbrProfile.DATA_SAVER_MAX_WIDTH, AbrProfile.DATA_SAVER_MAX_HEIGHT)
                .build()
        }
        return player
    }
}
