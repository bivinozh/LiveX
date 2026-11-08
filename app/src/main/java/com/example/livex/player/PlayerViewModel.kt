package com.example.livex.player

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
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: PlaybackEngine,
) : MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(PlayerState()) {

    /** Separate from [state]: a Player is a live object, not immutable UI data. */
    val player: StateFlow<Player?> = engine.player

    init {
        engine.metrics
            .onEach { metrics -> setState { copy(metrics = metrics) } }
            .launchIn(viewModelScope)
        engine.start(currentState.stream, currentState.config)
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

    override fun onCleared() {
        engine.release()
    }
}
