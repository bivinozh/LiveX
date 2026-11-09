package com.example.livex.player

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.livex.data.Stream
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.QoeTracker
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
                    val sample = current.sample()
                    tracker.onBufferedDuration(sample.bufferedDurationMs)
                    _stats.value = StatsState.from(tracker.metrics.value, sample)
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
        tracker.reset(nowMs = SystemClock.elapsedRealtime())

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
            it.release()
        }
        _player.value = null
    }

    private fun buildPlayer(config: PlayerConfig): ExoPlayer {
        // Topic: Startup & buffer tuning
        val buffer = if (config.tunedBuffer) BufferProfile.Tuned else BufferProfile.Default
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
        // DefaultDataSource adds support for non-HTTP URIs (file://, asset://, content://) on top.
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        // DefaultMediaSourceFactory picks HlsMediaSource / DashMediaSource / ProgressiveMediaSource
        // per MediaItem (from its MIME type, else the URI extension); all of them load through our factory.
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

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
