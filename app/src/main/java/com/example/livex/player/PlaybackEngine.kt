package com.example.livex.player

import androidx.media3.common.Player
import com.example.livex.data.Stream
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.StatsState
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

    /** Stats overlay snapshot, refreshed every 500 ms (events + polled player values). */
    val stats: StateFlow<StatsState>

    /** Current playback position of the current item, or 0 if nothing is loaded. */
    val currentPositionMs: Long

    /**
     * (Re)builds the player with [config] and plays [stream].
     * @param startPositionMs explicit position (e.g. restored after process death). When null,
     * the same VOD resumes where it was, a new stream starts at 0, and live joins the live edge.
     */
    fun start(stream: Stream, config: PlayerConfig, startPositionMs: Long? = null)

    /** Pauses playback (keeps decoders and buffer) and remembers whether to resume. */
    fun pause()

    /** Resumes playback only if it was playing when [pause] was called. */
    fun resume()

    /** After a playback error: prepare() again, continuing from the current position. */
    fun retry()

    /**
     * After ERROR_CODE_BEHIND_LIVE_WINDOW: jump to the live edge (seekToDefaultPosition) and
     * prepare() again. The segments at the old position have left the server's live window.
     */
    fun rejoinLiveEdge()

    /** Frees the player completely; the engine can't be used afterwards. */
    fun release()
}
