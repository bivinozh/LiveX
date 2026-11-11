package com.example.livex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.livex.feature.home.HomeScreen
import com.example.livex.feature.sessions.SessionsScreen
import com.example.livex.manifest.ManifestScreen
import com.example.livex.settings.BufferSettingsScreen
import com.example.livex.ui.player.PlayerScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/**
 * Navigation passes [streamId] to the player destination. Navigation also puts it into that
 * destination's SavedStateHandle under the property name "streamId" (see PlayerViewModel.KEY_STREAM_ID).
 */
@Serializable
data class PlayerRoute(val streamId: String)

/** Raw manifest viewer for a stream (see ManifestViewModel.KEY_STREAM_ID). */
@Serializable
data class ManifestRoute(val streamId: String)

/** Buffer tuning sliders and presets (DataStore). */
@Serializable
data object BufferSettingsRoute

/** Per-session QoE summaries from Room. */
@Serializable
data object SessionsRoute

@Composable
fun LiveXNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onStreamSelected = { streamId -> navController.navigate(PlayerRoute(streamId)) },
                onOpenSessions = { navController.navigate(SessionsRoute) },
            )
        }
        // Each destination gets its own ViewModelStore: leaving the player (Back) clears
        // PlayerViewModel, whose onCleared() releases the ExoPlayer.
        composable<PlayerRoute> {
            PlayerScreen(
                onOpenManifest = { streamId -> navController.navigate(ManifestRoute(streamId)) },
                onOpenBufferSettings = { navController.navigate(BufferSettingsRoute) },
            )
        }
        composable<BufferSettingsRoute> {
            // Saved: back to the player, which rebuilds with the new LoadControl on ON_START.
            BufferSettingsScreen(onSaved = { navController.popBackStack() })
        }
        composable<ManifestRoute> {
            ManifestScreen()
        }
        composable<SessionsRoute> {
            SessionsScreen()
        }
    }
}
