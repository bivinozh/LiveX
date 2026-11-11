package com.example.livex.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.livex.player.PlaybackEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Publishes the engine's player as a MediaSession, so playback can outlive the screen.
 *
 * - MediaSessionService: Media3 shows a media notification (MediaStyle: title, artwork,
 *   play/pause/seek) for the session and promotes this service to a FOREGROUND service of type
 *   mediaPlayback while it plays, so the process isn't killed in the background. The same session
 *   drives lock-screen controls, headset/Bluetooth buttons, Assistant, Wear OS and the TV "now
 *   playing" card.
 * - It does NOT own the player: [PlaybackEngine] builds/rebuilds it (settings changes) and the
 *   player screen decides when playback ends. Here we only follow engine.player: wrap the first
 *   player in a session, swap rebuilt players into the same session (connected controllers keep
 *   working), and release the session + stop when the engine has no player.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var engine: PlaybackEngine

    private var session: MediaSession? = null
    private lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        scope = MainScope()
        // Synchronously first: the UI's controller connects (onGetSession) right after onCreate, before
        // a dispatched collector would have run, and a null session would reject it.
        onPlayerChanged(engine.player.value)
        engine.player
            .onEach(::onPlayerChanged) // later rebuilds / release (the first, repeated value is a no-op)
            .launchIn(scope)
    }

    private fun onPlayerChanged(player: Player?) {
        val current = session
        when {
            player == null -> {
                // Playback ended for good (player screen closed): no session, no notification.
                current?.let { removeSession(it); it.release() }
                session = null
                stopSelf()
            }
            current == null -> session = MediaSession.Builder(this, player)
                // Tapping the notification / lock-screen card brings the app back.
                .setSessionActivity(launchAppIntent())
                .build()
                .also { addSession(it) }
            current.player !== player -> current.player = player
        }
    }

    /** A controller (our UI, the system notification, a headset...) wants to connect. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "Controller connecting: ${controllerInfo.packageName}")
        return session // null = nothing to control yet; the connection is rejected
    }

    /**
     * App swiped away from Recents. The player screen's ViewModel is cleared with the task, which
     * releases the player anyway; stop explicitly in case nothing is playing.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // Release the session only: the player belongs to the engine.
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun launchAppIntent(): PendingIntent {
        // The launcher intent brings the existing task (and the player screen) to the front.
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "LiveX-Session"
    }
}
