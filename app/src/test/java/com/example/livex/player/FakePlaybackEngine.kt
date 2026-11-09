package com.example.livex.player

import androidx.media3.common.Player
import com.example.livex.data.Stream
import com.example.livex.network.ThrottleMode
import com.example.livex.player.abr.QualityState
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.StatsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records calls instead of playing anything. */
class FakePlaybackEngine : PlaybackEngine {
    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val metrics = MutableStateFlow(QoeMetrics())
    override val stats = MutableStateFlow(StatsState())
    override val quality = MutableStateFlow(QualityState())
    val selectedQualities = mutableListOf<Int?>()

    override fun selectQuality(height: Int?) {
        selectedQualities += height
    }
    override var currentPositionMs: Long = 0

    val startedConfigs = mutableListOf<PlayerConfig>()
    val startedStreams = mutableListOf<Stream>()
    val startPositions = mutableListOf<Long?>()
    var pauseCount = 0
        private set
    var resumeCount = 0
        private set
    var released = false
        private set
    var retryCount = 0
        private set
    var rejoinLiveEdgeCount = 0
        private set

    val seekOffsets = mutableListOf<Long>()
    val throttleModes = mutableListOf<ThrottleMode>()

    override fun setThrottleMode(mode: ThrottleMode) {
        throttleModes += mode
    }

    override fun seekBy(offsetMs: Long) {
        seekOffsets += offsetMs
    }

    override fun retry() {
        retryCount++
    }

    override fun rejoinLiveEdge() {
        rejoinLiveEdgeCount++
    }

    override fun start(stream: Stream, config: PlayerConfig, startPositionMs: Long?) {
        startedConfigs += config
        startedStreams += stream
        startPositions += startPositionMs
    }

    override fun pause() {
        pauseCount++
    }

    override fun resume() {
        resumeCount++
    }

    override fun release() {
        released = true
    }
}
