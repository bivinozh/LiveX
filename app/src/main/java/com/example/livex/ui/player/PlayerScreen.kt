package com.example.livex.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.livex.core.mvi.CollectEffects
import com.example.livex.player.PlayerEffect
import com.example.livex.player.PlayerViewModel

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PlayerEffect.ShowMessage ->
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            // Runs once per composition (again after rotation): a NEW PlayerView for the SAME player.
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // The guide overlay owns D-pad focus; the built-in controller would compete for it.
                    useController = false
                }
            },
            // Re-attaches whenever the engine rebuilds the player after an optimization switch.
            update = { playerView -> playerView.player = player },
            // Detach so the long-lived player doesn't keep a reference to a destroyed Activity's view.
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        GuideOverlay(state = state, onIntent = viewModel::onIntent)
    }
}
