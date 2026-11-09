package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.data.SampleStreams
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

    init {
        engine.metrics
            .onEach { metrics -> setState { copy(metrics = metrics) } }
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
            PlayerIntent.ToggleOptimization -> toggleOptimization()
            PlayerIntent.OpenManifest -> sendEffect(PlayerEffect.NavigateToManifest(currentState.stream.id))
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
        // The screen is gone for good (e.g. Back to the list): free decoders, buffers and network.
        engine.release()
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
