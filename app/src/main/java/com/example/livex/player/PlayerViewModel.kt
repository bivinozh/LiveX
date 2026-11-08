package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.livex.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * The player itself lives in [PlaybackEngine], which this ViewModel owns, so playback
 * survives configuration changes such as rotation; only the PlayerView is recreated.
 *
 * [SavedStateHandle] covers the case the ViewModel does NOT survive: the system killing the
 * app process while it is in the background. The position is written on ON_STOP and read back
 * in init when the app is reopened.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: PlaybackEngine,
    private val savedState: SavedStateHandle,
) : MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(PlayerState()) {

    /** Separate from [state]: a Player is a live object, not immutable UI data. */
    val player: StateFlow<Player?> = engine.player

    init {
        engine.metrics
            .onEach { metrics -> setState { copy(metrics = metrics) } }
            .launchIn(viewModelScope)

        restoreSavedState()
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
            PlayerIntent.NextStream -> switchStream(step = 1)
            PlayerIntent.PreviousStream -> switchStream(step = -1)
            PlayerIntent.ToggleGuide -> setState { copy(isGuideVisible = !isGuideVisible) }
            PlayerIntent.ToggleOptimization -> toggleOptimization()
            PlayerIntent.LifecycleStart -> engine.resume()
            PlayerIntent.LifecycleStop -> {
                engine.pause()
                saveState()
            }
        }
    }

    private fun switchStream(step: Int) {
        setState {
            copy(streamIndex = (streamIndex + step + streams.size) % streams.size)
        }
        // Same optimizations, new content: the engine starts it from the beginning (or live edge).
        engine.start(currentState.stream, currentState.config)
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
        savedState[KEY_STREAM_INDEX] = currentState.streamIndex
        savedState[KEY_TUNED_BUFFER] = currentState.config.tunedBuffer
        savedState[KEY_DATA_SAVER_ABR] = currentState.config.dataSaverAbr
        savedState[KEY_OKHTTP_NETWORK] = currentState.config.okHttpNetwork
    }

    private fun restoreSavedState() {
        setState {
            copy(
                streamIndex = savedState.get<Int>(KEY_STREAM_INDEX)?.takeIf { it in streams.indices } ?: 0,
                config = PlayerConfig(
                    tunedBuffer = savedState.get<Boolean>(KEY_TUNED_BUFFER) ?: false,
                    dataSaverAbr = savedState.get<Boolean>(KEY_DATA_SAVER_ABR) ?: false,
                    okHttpNetwork = savedState.get<Boolean>(KEY_OKHTTP_NETWORK) ?: false,
                ),
            )
        }
    }

    override fun onCleared() {
        // The screen is gone for good: free decoders, buffers, network and the wake lock.
        engine.release()
    }

    companion object {
        const val KEY_POSITION_MS = "player.positionMs"
        const val KEY_STREAM_INDEX = "player.streamIndex"
        const val KEY_TUNED_BUFFER = "player.tunedBuffer"
        const val KEY_DATA_SAVER_ABR = "player.dataSaverAbr"
        const val KEY_OKHTTP_NETWORK = "player.okHttpNetwork"
    }
}
