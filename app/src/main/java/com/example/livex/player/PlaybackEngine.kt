package com.example.livex.player

import androidx.media3.common.Player
import com.example.livex.data.Stream
import androidx.media3.ui.PlayerView
import com.example.livex.network.ThrottleMode
import com.example.livex.player.ads.AdSeekPolicy
import com.example.livex.player.ads.AdState
import com.example.livex.player.ads.SeekOutcome
import com.example.livex.player.ads.VodProgress
import com.example.livex.player.live.LiveWindow
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

    /** Ad currently playing (client-side ads), polled every 500 ms. */
    val ad: StateFlow<AdState>

    /**
     * The on-screen PlayerView, used by IMA as AdViewProvider (ad UI + viewability).
     * Set when the view is created, null when it's released so the Activity isn't leaked.
     */
    fun setAdView(view: PlayerView?)

    /** CONTENT position of the current item (not the ad's), or 0 if nothing is loaded. */
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

    /** Live (DVR) window, refreshed every 500 ms; null for VOD or before the timeline is known. */
    val live: StateFlow<LiveWindow?>

    /** GO LIVE: seekToDefaultPosition() = live edge minus the target offset. */
    fun goLive()

    /** Network throttle simulator: applies to the next bytes read, without rebuilding the player. */
    fun setThrottleMode(mode: ThrottleMode)

    /** VOD content progress + ad breaks (for the seek bar markers), every 500 ms; null for live. */
    val progress: StateFlow<VodProgress?>

    /**
     * Seeks relative to the current content position (negative = back), applying [AdSeekPolicy]:
     * no seeking during ads, and seeking over an unwatched break plays it first.
     */
    fun seekBy(offsetMs: Long): SeekOutcome

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
