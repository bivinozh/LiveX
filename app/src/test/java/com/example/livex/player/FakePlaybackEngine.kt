package com.example.livex.player

import androidx.media3.common.Player
import com.example.livex.data.Stream
import com.example.livex.qoe.QoeMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records calls instead of playing anything. */
class FakePlaybackEngine : PlaybackEngine {
    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val metrics = MutableStateFlow(QoeMetrics())

    val startedConfigs = mutableListOf<PlayerConfig>()
    val startedStreams = mutableListOf<Stream>()
    var released = false
        private set

    override fun start(stream: Stream, config: PlayerConfig) {
        startedConfigs += config
        startedStreams += stream
    }

    override fun release() {
        released = true
    }
}
