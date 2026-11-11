package com.example.livex.ui.player

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.livex.playback.PlaybackService

/**
 * Connects to PlaybackService's MediaSession while this composable is on screen.
 *
 * A MediaController is a Player too: PlayerView renders and controls through it exactly like an
 * ExoPlayer, but every call goes to the session (same process here; a separate process or another
 * app would work the same way). It survives the engine swapping in a rebuilt player.
 * Null until connected (asynchronous: binding the service takes a moment).
 */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            // Releases the controller (or cancels a pending connection). The session and playback go
            // on: closing the screen doesn't stop playback, the engine's release() does.
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** playWhenReady of [player]: does the viewer want it to play (true even while buffering)? */
@Composable
fun rememberPlayWhenReady(player: Player?): Boolean {
    var playWhenReady by remember(player) { mutableStateOf(player?.playWhenReady == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {
                playWhenReady = value
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return playWhenReady
}
