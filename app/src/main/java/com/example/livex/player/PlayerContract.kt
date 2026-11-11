package com.example.livex.player

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.data.SampleStreams
import com.example.livex.data.Stream
import com.example.livex.data.Topic
import com.example.livex.data.Topics
import com.example.livex.network.ThrottleMode
import com.example.livex.player.ads.AdState
import com.example.livex.player.drm.WidevineInfo
import com.example.livex.player.live.LiveLatency
import com.example.livex.qoe.QoeMetrics

data class PlayerState(
    /** The stream chosen on the Home screen (navigation argument). */
    val stream: Stream = SampleStreams.all.first(),
    val topics: List<Topic> = Topics.all,
    val topicIndex: Int = 0,
    val config: PlayerConfig = PlayerConfig(),
    val metrics: QoeMetrics = QoeMetrics(),
    val isGuideVisible: Boolean = true,
    /** Stats overlay toggle. The stats themselves stream separately (PlayerViewModel.stats). */
    val isStatsVisible: Boolean = true,
    /** Network throttle simulator mode (applied at runtime, no player rebuild). */
    val throttleMode: ThrottleMode = ThrottleMode.UNLIMITED,
    /** Player.Listener events mapped for the UI: spinner on Buffering, error card on Error. */
    val playback: PlayerUiState = PlayerUiState.Idle,
    /** Client-side ad playing right now (for the on-video AD badge). */
    val ad: AdState = AdState(),
    /** This device's Widevine level (shown for protected streams). */
    val widevine: WidevineInfo? = null,
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
    data object ToggleStats : PlayerIntent
    data object OpenManifest : PlayerIntent

    /** "Edit buffer settings" in the buffer topic. */
    data object OpenBufferSettings : PlayerIntent

    /** Retry button on the error card. */
    data object Retry : PlayerIntent

    /** Seek buttons in the guide: relative jump, negative = back. */
    data class SeekBy(val offsetMs: Long) : PlayerIntent

    /** Network dropdown: simulate a slower or unstable connection. */
    data class SetThrottleMode(val mode: ThrottleMode) : PlayerIntent

    /** GO LIVE button: back to the live edge (minus the target offset). */
    data object GoLive : PlayerIntent

    /** Live target latency buttons (3 s / 10 s / 30 s). */
    data class SetLiveLatency(val latency: LiveLatency) : PlayerIntent

    /** App came to the foreground (Lifecycle ON_START). */
    data object LifecycleStart : PlayerIntent

    /** App went to the background (Lifecycle ON_STOP). */
    data object LifecycleStop : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data class ShowMessage(val message: String) : PlayerEffect
    data class NavigateToManifest(val streamId: String) : PlayerEffect
    data object NavigateToBufferSettings : PlayerEffect
}
