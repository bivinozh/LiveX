package com.example.livex.player

import androidx.media3.common.Player
import com.example.livex.data.Stream
import com.example.livex.qoe.QoeMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the actual player. Kept behind an interface so [PlayerViewModel] can be unit-tested
 * with a fake, without ExoPlayer, a Looper or the network.
 */
interface PlaybackEngine {
    /** The current player instance; changes whenever [start] rebuilds it with a new config. */
    val player: StateFlow<Player?>

    /** Live QoE numbers for the current player instance. */
    val metrics: StateFlow<QoeMetrics>

    /** (Re)builds the player with [config] and plays [stream], continuing from the current position. */
    fun start(stream: Stream, config: PlayerConfig)

    fun release()
}
