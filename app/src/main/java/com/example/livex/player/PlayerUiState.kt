package com.example.livex.player

/**
 * What the player is doing, as the UI needs it. Mirrors Player.STATE_* plus an error case:
 *
 * - Idle: no media prepared yet (or stopped).
 * - Buffering: can't play yet, loading data. Startup, seeks and rebuffers all land here.
 * - Ready: enough data buffered to play (playing or paused, see playWhenReady).
 * - Ended: reached the end of a VOD. Live streams never end.
 * - Error: playback stopped with a PlaybackException; see [Error].
 */
sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data object Buffering : PlayerUiState
    data object Ready : PlayerUiState
    data object Ended : PlayerUiState
    /**
     * @property message user-facing explanation (see PlaybackErrors).
     * @property code Media3 error code name, e.g. ERROR_CODE_IO_BAD_HTTP_STATUS, shown for learning/debugging.
     */
    data class Error(val message: String, val code: String) : PlayerUiState
}
