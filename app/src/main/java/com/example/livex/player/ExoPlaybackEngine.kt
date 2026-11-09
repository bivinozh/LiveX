package com.example.livex.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import com.example.livex.data.Stream
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.QoeTracker
import com.example.livex.network.NetworkThrottle
import com.example.livex.player.abr.AbrTracker
import com.example.livex.player.abr.QualityOptions
import com.example.livex.player.abr.QualityState
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
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker = QoeTracker()

    private var currentStream: Stream? = null
    private var resumeOnStart = false
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()
    override val metrics: StateFlow<QoeMetrics> = tracker.metrics
    private val _stats = MutableStateFlow(StatsState())
    private val bufferHistory = BufferHistory()

    // Track selection & ABR
    private var trackSelector: DefaultTrackSelector? = null
    private val abrTracker = AbrTracker(bufferedMs = { _player.value?.totalBufferedDuration ?: 0L })
    private val _quality = MutableStateFlow(QualityState())
    override val quality: StateFlow<QualityState> = _quality.asStateFlow()

    /** Rebuilds the quality list whenever the available tracks change (manifest parsed, period change...). */
    private val tracksListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val hadOptions = _quality.value.options.isNotEmpty()
            _quality.value = _quality.value.copy(options = QualityOptions.from(tracks))
            // A manual choice survives player rebuilds: re-apply it once the new player knows its tracks.
            if (!hadOptions && _quality.value.selectedHeight != null) applyQualityOverride()
        }
    }

    /** One simulated link for the engine's lifetime: survives player rebuilds, switchable at runtime. */
    private val throttle = NetworkThrottle()

    private val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    override val stats: StateFlow<StatsState> = _stats.asStateFlow()
    override val currentPositionMs: Long
        get() = _player.value?.currentPosition ?: 0L

    init {
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
                    _stats.value = StatsState.from(metrics, sample, bufferHistory.points, abrTracker.switches.value)
                } else {
                    _stats.value = StatsState()
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
    )

    override fun start(stream: Stream, config: PlayerConfig, startPositionMs: Long?) {
        // An explicit position wins (restore after process death). Otherwise resume only when
        // rebuilding the same VOD (optimization switch); a new stream starts from the beginning,
        // and live always joins at the default live position.
        val resumePositionMs = startPositionMs ?: _player.value?.currentPosition
            ?.takeIf { stream == currentStream && !stream.isLive }
        currentStream = stream
        releasePlayer()

        val newPlayer = buildPlayer(config)
        newPlayer.addAnalyticsListener(tracker)
        newPlayer.addAnalyticsListener(abrTracker)
        newPlayer.addListener(tracksListener)
        abrTracker.reset()
        _quality.value = _quality.value.copy(options = emptyList()) // keep the manual choice, if any
        // Debug builds: log every load, format switch and state change to logcat (tag "EventLogger").
        // The quickest way to see what ABR downloads (downstreamFormat) vs. what plays (videoInputFormat).
        if (isDebuggable) newPlayer.addAnalyticsListener(EventLogger())
        // The play request: startup (VST) is measured from here to the first rendered frame.
        tracker.reset(nowMs = SystemClock.elapsedRealtime())
        bufferHistory.clear()

        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
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

    /**
     * Manual quality: pins video to one track with a TrackSelectionOverride. null = Auto (ABR).
     * Applied through the track selector's parameters, so no rebuild: the player re-selects tracks
     * and the next segments come in the chosen rendition (already buffered ones still play first).
     */
    override fun selectQuality(height: Int?) {
        _quality.value = _quality.value.copy(selectedHeight = height)
        applyQualityOverride()
    }

    private fun applyQualityOverride() {
        val selector = trackSelector ?: return
        val player = _player.value ?: return
        val params = selector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        val height = _quality.value.selectedHeight
        val option = height?.let { h -> _quality.value.options.firstOrNull { it.height == h } }
        if (option != null) {
            val group = player.currentTracks.groups[option.groupIndex].mediaTrackGroup
            params.addOverride(TrackSelectionOverride(group, option.trackIndex))
        }
        selector.setParameters(params)
    }

    override fun setThrottleMode(mode: ThrottleMode) {
        throttle.mode = mode
    }

    override fun seekBy(offsetMs: Long) {
        val current = _player.value ?: return
        // Clamp to the start; seeking past the end/live edge is clamped by the player itself.
        current.seekTo((current.currentPosition + offsetMs).coerceAtLeast(0))
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
        releasePlayer()
    }

    private fun releasePlayer() {
        _player.value?.let {
            it.removeAnalyticsListener(tracker)
            it.removeAnalyticsListener(abrTracker)
            it.removeListener(tracksListener)
            it.release()
        }
        _player.value = null
        trackSelector = null
    }

    private fun buildPlayer(config: PlayerConfig): ExoPlayer {
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
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        // Topic: ABR. AdaptiveTrackSelection decides WHICH rendition to load next; the factory's knobs
        // set how eagerly it goes up/down. DefaultTrackSelector applies constraints and overrides on top.
        val abr = config.abr
        val selector = DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory(
                abr.minDurationForQualityIncreaseMs,
                abr.maxDurationForQualityDecreaseMs,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                abr.bandwidthFraction,
            ),
        )
        if (config.dataSaverAbr) {
            selector.setParameters(
                selector.buildUponParameters()
                    .setMaxVideoSize(AbrProfile.DATA_SAVER_MAX_WIDTH, AbrProfile.DATA_SAVER_MAX_HEIGHT),
            )
        }
        trackSelector = selector

        // A fresh meter per player so the initial estimate is what decides the first rendition
        // (the app-wide singleton would carry over what earlier sessions measured).
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .apply { abr.initialBitrateEstimateBps?.let { setInitialBitrateEstimate(it) } }
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(selector)
            .setBandwidthMeter(bandwidthMeter)
            .build()
    }
}
