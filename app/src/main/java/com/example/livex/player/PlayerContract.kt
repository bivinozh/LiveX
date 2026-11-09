package com.example.livex.player

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.data.SampleStreams
import com.example.livex.data.Stream
import com.example.livex.data.Topic
import com.example.livex.data.Topics
import com.example.livex.qoe.QoeMetrics

data class PlayerState(
    /** The stream chosen on the Home screen (navigation argument). */
    val stream: Stream = SampleStreams.all.first(),
    val topics: List<Topic> = Topics.all,
    val topicIndex: Int = 0,
    val config: PlayerConfig = PlayerConfig(),
    val metrics: QoeMetrics = QoeMetrics(),
    val isGuideVisible: Boolean = true,
    /** Player.Listener events mapped for the UI: spinner on Buffering, error card on Error. */
    val playback: PlayerUiState = PlayerUiState.Idle,
) : UiState {
    val topic: Topic get() = topics[topicIndex]
    val isOptimizationEnabled: Boolean
        get() = topic.optimization?.let { config.isEnabled(it.toggle) } ?: false
}

sealed interface PlayerIntent : UiIntent {
    data object NextTopic : PlayerIntent
    data object PreviousTopic : PlayerIntent
    data object ToggleOptimization : PlayerIntent
    data object ToggleGuide : PlayerIntent
    data object OpenManifest : PlayerIntent

    /** Retry button on the error card. */
    data object Retry : PlayerIntent

    /** App came to the foreground (Lifecycle ON_START). */
    data object LifecycleStart : PlayerIntent

    /** App went to the background (Lifecycle ON_STOP). */
    data object LifecycleStop : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data class ShowMessage(val message: String) : PlayerEffect
    data class NavigateToManifest(val streamId: String) : PlayerEffect
}
