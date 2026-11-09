package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.data.SampleStreams
import com.example.livex.qoe.StatsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * The player itself lives in [PlaybackEngine], which this ViewModel owns, so playback
 * survives configuration changes such as rotation; only the PlayerView is recreated.
 *
 * [SavedStateHandle] carries two things:
 * - the navigation argument [KEY_STREAM_ID] (put there by Navigation from PlayerRoute), and
 * - the playback position + optimizations, written on ON_STOP so they survive process death.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: PlaybackEngine,
    private val savedState: SavedStateHandle,
) : MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(PlayerState()) {

    /** Separate from [state]: a Player is a live object, not immutable UI data. */
    val player: StateFlow<Player?> = engine.player

    /**
     * Stats overlay data, refreshed every 500 ms. Kept out of [state] on purpose: it's high-frequency
     * telemetry that only the overlay reads, so collecting it there (and only while visible) avoids
     * recomposing the whole player screen twice a second.
     */
    val stats: StateFlow<StatsState> = engine.stats

    /**
     * Translates player callbacks into [PlayerState.playback]. Callbacks arrive on the main thread
     * (the player's application looper), so updating state here is safe.
     */
    @VisibleForTesting
    internal val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // After an error the player also reports STATE_IDLE; keep the error card visible.
            if (playbackState == Player.STATE_IDLE && currentState.playback is PlayerUiState.Error) return
            setState { copy(playback = playbackState.toUiState()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Paused or stalled too long on a live stream: our position slid out of the DVR window.
                // Not the viewer's problem, so recover silently by jumping to the live edge.
                engine.rejoinLiveEdge()
                setState { copy(playback = PlayerUiState.Buffering) }
                return
            }
            setState {
                copy(playback = PlayerUiState.Error(PlaybackErrors.message(error), error.errorCodeName))
            }
        }
    }

    /** The player instance [playerListener] is registered on. */
    private var listenedPlayer: Player? = null

    init {
        engine.metrics
            .onEach { metrics -> setState { copy(metrics = metrics) } }
            .launchIn(viewModelScope)

        // The engine builds a NEW player on every optimization switch: move the listener along.
        engine.player
            .onEach { newPlayer ->
                listenedPlayer?.removeListener(playerListener)
                newPlayer?.addListener(playerListener)
                listenedPlayer = newPlayer
                // A fresh player starts clean (any old error belonged to the previous instance).
                setState { copy(playback = newPlayer?.playbackState?.toUiState() ?: PlayerUiState.Idle) }
            }
            .launchIn(viewModelScope)

        restoreState()
        val restoredPositionMs = savedState.get<Long>(KEY_POSITION_MS)
            ?.takeUnless { currentState.stream.isLive } // live: always join at the live edge
        engine.start(currentState.stream, currentState.config, restoredPositionMs)
    }

    override fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.NextTopic -> setState {
                copy(topicIndex = (topicIndex + 1) % topics.size)
            }
            PlayerIntent.PreviousTopic -> setState {
                copy(topicIndex = (topicIndex - 1 + topics.size) % topics.size)
            }
            PlayerIntent.ToggleGuide -> setState { copy(isGuideVisible = !isGuideVisible) }
            PlayerIntent.ToggleStats -> setState { copy(isStatsVisible = !isStatsVisible) }
            PlayerIntent.ToggleOptimization -> toggleOptimization()
            PlayerIntent.OpenManifest -> sendEffect(PlayerEffect.NavigateToManifest(currentState.stream.id))
            is PlayerIntent.SeekBy -> engine.seekBy(intent.offsetMs)
            is PlayerIntent.SetThrottleMode -> {
                // Unlike the optimization toggles, no engine.start(): the data source reads the mode
                // on every read, so playback (and its buffer) carries on under the new conditions.
                engine.setThrottleMode(intent.mode)
                setState { copy(throttleMode = intent.mode) }
            }
            PlayerIntent.Retry -> {
                engine.retry()
                // Hide the error card right away; player callbacks take over from here.
                setState { copy(playback = PlayerUiState.Buffering) }
            }
            PlayerIntent.LifecycleStart -> engine.resume()
            PlayerIntent.LifecycleStop -> {
                engine.pause()
                saveState()
            }
        }
    }

    private fun toggleOptimization() {
        val optimization = currentState.topic.optimization ?: return
        val newConfig = currentState.config.toggled(optimization.toggle)
        setState { copy(config = newConfig) }
        // Buffer, network and track-selection settings are fixed at build time, so rebuild the player.
        engine.start(currentState.stream, newConfig)
        val label = if (newConfig.isEnabled(optimization.toggle)) {
            optimization.optimizedLabel
        } else {
            optimization.defaultLabel
        }
        sendEffect(PlayerEffect.ShowMessage("Player restarted. $label"))
    }

    private fun saveState() {
        savedState[KEY_POSITION_MS] = engine.currentPositionMs
        savedState[KEY_TUNED_BUFFER] = currentState.config.tunedBuffer
        savedState[KEY_DATA_SAVER_ABR] = currentState.config.dataSaverAbr
        savedState[KEY_OKHTTP_NETWORK] = currentState.config.okHttpNetwork
    }

    private fun restoreState() {
        val defaults = PlayerConfig()
        setState {
            copy(
                stream = SampleStreams.byId(savedState.get<String>(KEY_STREAM_ID)) ?: SampleStreams.all.first(),
                config = PlayerConfig(
                    tunedBuffer = savedState.get<Boolean>(KEY_TUNED_BUFFER) ?: defaults.tunedBuffer,
                    dataSaverAbr = savedState.get<Boolean>(KEY_DATA_SAVER_ABR) ?: defaults.dataSaverAbr,
                    okHttpNetwork = savedState.get<Boolean>(KEY_OKHTTP_NETWORK) ?: defaults.okHttpNetwork,
                ),
            )
        }
    }

    override fun onCleared() {
        listenedPlayer?.removeListener(playerListener)
        listenedPlayer = null
        // The screen is gone for good (e.g. Back to the list): free decoders, buffers and network.
        engine.release()
    }

    private fun Int.toUiState(): PlayerUiState = when (this) {
        Player.STATE_BUFFERING -> PlayerUiState.Buffering
        Player.STATE_READY -> PlayerUiState.Ready
        Player.STATE_ENDED -> PlayerUiState.Ended
        else -> PlayerUiState.Idle
    }

    companion object {
        /** Must match the property name in navigation.PlayerRoute. */
        const val KEY_STREAM_ID = "streamId"
        const val KEY_POSITION_MS = "player.positionMs"
        const val KEY_TUNED_BUFFER = "player.tunedBuffer"
        const val KEY_DATA_SAVER_ABR = "player.dataSaverAbr"
        const val KEY_OKHTTP_NETWORK = "player.okHttpNetwork"
    }
}
