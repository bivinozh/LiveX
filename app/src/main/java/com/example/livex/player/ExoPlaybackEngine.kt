package com.example.livex.player

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
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

/** ExoPlayer-backed [PlaybackEngine]; one instance per PlayerViewModel (ViewModel-scoped in Hilt). */
@OptIn(UnstableApi::class)
class ExoPlaybackEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker = QoeTracker()

    private var currentStream: Stream? = null
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()
    override val metrics: StateFlow<QoeMetrics> = tracker.metrics

    init {
        // Buffered duration isn't an event, so sample it.
        scope.launch {
            while (isActive) {
                val current = _player.value
                if (current != null) tracker.onBufferedDuration(current.totalBufferedDuration)
                delay(500.milliseconds)
            }
        }
    }

    override fun start(stream: Stream, config: PlayerConfig) {
        // Resume only when rebuilding the same VOD (optimization switch). A new stream starts from
        // the beginning, and live always joins at the default live position.
        val resumePositionMs = _player.value?.currentPosition
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

        // Topic: Network
        val httpFactory: DataSource.Factory =
            if (config.okHttpNetwork) OkHttpDataSource.Factory(okHttpClient)
            else DefaultHttpDataSource.Factory()
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
