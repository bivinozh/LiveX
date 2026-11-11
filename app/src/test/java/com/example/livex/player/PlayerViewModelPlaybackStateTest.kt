package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.data.SampleStreams
import com.example.livex.settings.FakeBufferSettingsRepository
import com.example.livex.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Player.Listener -> PlayerUiState. Drives the ViewModel's listener directly (no real player).
 * Robolectric because PlaybackException reads SystemClock in its constructor.
 */
@RunWith(AndroidJUnit4::class)
class PlayerViewModelPlaybackStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var engine: FakePlaybackEngine
    private lateinit var viewModel: PlayerViewModel

    private val playback get() = viewModel.state.value.playback

    @Before
    fun setUp() {
        engine = FakePlaybackEngine()
        viewModel = PlayerViewModel(
            engine,
            FakeBufferSettingsRepository(),
            SavedStateHandle(mapOf(PlayerViewModel.KEY_STREAM_ID to SampleStreams.liveHls.id)),
        )
    }

    private fun error(code: Int) = PlaybackException("test", null, code)

    @Test
    fun `starts idle`() {
        assertEquals(PlayerUiState.Idle, playback)
    }

    @Test
    fun `player states map to ui states`() {
        val listener = viewModel.playerListener

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(PlayerUiState.Buffering, playback)

        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlayerUiState.Ready, playback)

        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(PlayerUiState.Ended, playback)

        listener.onPlaybackStateChanged(Player.STATE_IDLE)
        assertEquals(PlayerUiState.Idle, playback)
    }

    @Test
    fun `player error shows a friendly message and the error code name`() {
        viewModel.playerListener.onPlayerError(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        val state = playback as PlayerUiState.Error
        assertEquals(PlaybackErrors.NETWORK, state.message)
        assertEquals("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", state.code)
    }

    @Test
    fun `decoder failures on a widevine stream are explained as DRM`() {
        val drmViewModel = PlayerViewModel(
            FakePlaybackEngine(),
            FakeBufferSettingsRepository(),
            SavedStateHandle(mapOf(PlayerViewModel.KEY_STREAM_ID to SampleStreams.widevineL1Only.id)),
        )

        drmViewModel.playerListener.onPlayerError(error(PlaybackException.ERROR_CODE_DECODING_FAILED))

        assertEquals(PlaybackErrors.DRM_DECRYPT_FAILED, (drmViewModel.state.value.playback as PlayerUiState.Error).message)
    }

    @Test
    fun `the idle state that follows an error does not hide the error`() {
        viewModel.playerListener.onPlayerError(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        viewModel.playerListener.onPlaybackStateChanged(Player.STATE_IDLE)

        assertTrue(playback is PlayerUiState.Error)
    }

    @Test
    fun `behind live window rejoins the live edge instead of showing an error`() {
        viewModel.playerListener.onPlayerError(error(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))

        assertEquals(1, engine.rejoinLiveEdgeCount)
        assertEquals(PlayerUiState.Buffering, playback)
    }

    @Test
    fun `retry re-prepares the player and leaves the error state`() {
        viewModel.playerListener.onPlayerError(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        viewModel.onIntent(PlayerIntent.Retry)

        assertEquals(1, engine.retryCount)
        assertEquals(PlayerUiState.Buffering, playback)
    }
}
