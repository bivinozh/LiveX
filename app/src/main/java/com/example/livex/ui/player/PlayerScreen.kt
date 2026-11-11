package com.example.livex.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.graphics.toRect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.livex.core.mvi.CollectEffects
import com.example.livex.player.PlayerEffect
import com.example.livex.player.PlayerIntent
import com.example.livex.player.PlayerUiState
import com.example.livex.player.PlayerViewModel

@Composable
fun PlayerScreen(
    onOpenManifest: (streamId: String) -> Unit,
    onOpenBufferSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The video and its transport go through the MediaSession (PlaybackService), like the
    // notification and lock screen do. Lab controls (seek policy, throttle, stats) use the ViewModel.
    val controller = rememberMediaController()
    val inPip = rememberIsInPictureInPictureMode()
    var videoBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    PlayerLifecycleEffect(onIntent = viewModel::onIntent)

    if (state.supportsPictureInPicture) {
        PictureInPictureEffect(
            enabled = rememberPlayWhenReady(controller) && state.playback !is PlayerUiState.Error,
            videoWidth = state.metrics.videoWidth,
            videoHeight = state.metrics.videoHeight,
            sourceRect = videoBounds,
        )
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PlayerEffect.ShowMessage ->
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            // Navigating away stops this destination: the lifecycle effect pauses playback (ON_STOP)
            // and resumes it when Back returns here (ON_START).
            is PlayerEffect.NavigateToManifest -> onOpenManifest(effect.streamId)
            // Same pause/resume as the manifest viewer; saved settings are applied on return.
            PlayerEffect.NavigateToBufferSettings -> onOpenBufferSettings()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            // Runs once per composition (again after rotation): a NEW PlayerView for the SAME player.
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // The guide overlay owns D-pad focus; the built-in controller would compete for it.
                    useController = false
                    // IMA draws ad UI in this view's ad overlay (AdViewProvider).
                    viewModel.setAdView(this)
                }
            },
            // The controller, not the ExoPlayer: it stays connected when the engine rebuilds the player.
            update = { playerView -> playerView.player = controller },
            // Detach so the long-lived player doesn't keep a reference to a destroyed Activity's view.
            onRelease = { playerView ->
                playerView.player = null
                viewModel.setAdView(null)
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { videoBounds = it.boundsInWindow().toAndroidRectF().toRect() },
        )

        // PiP window: video only, no overlays.
        if (inPip) return@Box

        if (state.isStatsVisible) {
            // Collected only while visible: when hidden, nothing recomposes on the 500 ms ticks.
            val stats by viewModel.stats.collectAsStateWithLifecycle()
            StatsOverlay(stats, Modifier.align(Alignment.TopStart).padding(24.dp))
        }

        if (state.stream.isLive) {
            // Collected only for live streams; bottom of the video area, left of the guide panel.
            val live by viewModel.live.collectAsStateWithLifecycle()
            live?.let { window ->
                LiveControlBar(
                    live = window,
                    latency = state.config.liveLatency,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 24.dp, end = 24.dp + if (state.isGuideVisible) GuidePanelWidth else 0.dp),
                )
            }
        } else {
            // VOD: seek bar with ad break markers (client-side VMAP or server-side DAI breaks).
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            progress?.let {
                VodProgressBar(
                    progress = it,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 24.dp, end = 24.dp + if (state.isGuideVisible) GuidePanelWidth else 0.dp),
                )
            }
        }

        // Top-right of the video area (left of the guide panel), like broadcast TV.
        AdBadge(
            ad = state.ad,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp + if (state.isGuideVisible) GuidePanelWidth else 0.dp),
        )

        GuideOverlay(state = state, onIntent = viewModel::onIntent)

        // Drawn last so the error card sits above the guide; centered in the visible video area.
        PlaybackStatusOverlay(
            playback = state.playback,
            onRetry = { viewModel.onIntent(PlayerIntent.Retry) },
            modifier = Modifier.padding(end = if (state.isGuideVisible) GuidePanelWidth else 0.dp),
        )
    }
}

/**
 * Forwards ON_START / ON_STOP to the ViewModel while this screen is in composition.
 * ON_STOP (not ON_PAUSE): in multi-window and PiP the Activity is paused but still visible.
 */
@Composable
private fun PlayerLifecycleEffect(onIntent: (PlayerIntent) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnIntent by rememberUpdatedState(onIntent)

    val activity = LocalContext.current.findActivity()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentOnIntent(PlayerIntent.LifecycleStart)
                Lifecycle.Event.ON_STOP -> {
                    // Stopped while still in PiP mode = the user dismissed the PiP window.
                    if (activity?.isInPictureInPictureMode == true) {
                        currentOnIntent(PlayerIntent.PictureInPictureClosed)
                    }
                    // This lifecycle is the navigation destination's. If the Activity is still started,
                    // only another screen of the app covered the player; otherwise the app left the screen.
                    val appStillVisible = (activity as? LifecycleOwner)
                        ?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
                    currentOnIntent(
                        if (appStillVisible) PlayerIntent.CoveredByOtherScreen else PlayerIntent.LifecycleStop,
                    )
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Leaving composition (or a new lifecycle owner): stop listening, so nothing leaks.
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
