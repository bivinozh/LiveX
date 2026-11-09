package com.example.livex.ui.player

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.PlayerUiState
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackStatusOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var retryCount = 0

    private fun setContent(playback: PlayerUiState) {
        composeRule.setContent {
            LiveXTheme { PlaybackStatusOverlay(playback = playback, onRetry = { retryCount++ }) }
        }
    }

    @Test
    fun buffering_showsSpinner() {
        setContent(PlayerUiState.Buffering)

        composeRule.onNodeWithContentDescription(BUFFERING_DESCRIPTION).assertExists()
    }

    @Test
    fun ready_showsNothing() {
        setContent(PlayerUiState.Ready)

        composeRule.onNodeWithContentDescription(BUFFERING_DESCRIPTION).assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun error_showsMessageAndCode() {
        setContent(PlayerUiState.Error(message = "Stream not found.", code = "ERROR_CODE_IO_BAD_HTTP_STATUS"))

        composeRule.onNodeWithText("Stream not found.").assertExists()
        composeRule.onNodeWithText("ERROR_CODE_IO_BAD_HTTP_STATUS", substring = true).assertExists()
        composeRule.onNodeWithContentDescription(BUFFERING_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun error_focusesRetry_andRetryCallsBack() {
        setContent(PlayerUiState.Error(message = "Boom", code = "X"))

        composeRule.onNodeWithText("Retry").assertIsFocused()
        composeRule.onNodeWithText("Retry").performDpadClick()

        assertEquals(1, retryCount)
    }
}
