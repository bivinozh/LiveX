package com.example.livex.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.PlayerIntent
import com.example.livex.player.ads.AdBreak
import com.example.livex.player.ads.VodProgress
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land")
@OptIn(ExperimentalTestApi::class)
class VodProgressBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<PlayerIntent>()
    private var playPauseCount = 0

    private val progress = VodProgress(
        positionMs = 83_000,
        durationMs = 596_000,
        adBreaks = listOf(AdBreak(0, isPlayed = true), AdBreak(298_000, isPlayed = false)),
    )

    private fun setContent(isPlaying: Boolean = true) = composeRule.setContent {
        LiveXTheme {
            VodProgressBar(
                progress = progress,
                onIntent = { sent += it },
                isPlaying = isPlaying,
                onPlayPause = { playPauseCount++ },
            )
        }
    }

    @Test
    fun okOnTheSeekBarTogglesPlayPause() {
        setContent()
        val bar = composeRule.onNodeWithTag(VOD_BAR_TAG)

        bar.performSemanticsAction(SemanticsActions.RequestFocus)
        bar.performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(1, playPauseCount)
        assertEquals(emptyList<PlayerIntent>(), sent)
    }

    @Test
    fun playPauseButtonShowsTheCurrentStateAndToggles() {
        setContent(isPlaying = true)

        composeRule.onNodeWithText("⏸ Pause").performDpadClick()

        assertEquals(1, playPauseCount)
    }

    @Test
    fun showsTimeAndAdBreakSummary() {
        setContent()

        composeRule.onNodeWithText("1:23 / 9:56").assertExists()
        composeRule.onNodeWithText("2 ad breaks · 1 watched").assertExists()
        composeRule.onNodeWithContentDescription("Progress 1:23 / 9:56, 2 ad breaks · 1 watched").assertExists()
    }

    @Test
    fun leftAndRightSeekTenSeconds() {
        setContent()
        val bar = composeRule.onNodeWithTag(VOD_BAR_TAG)

        bar.performSemanticsAction(SemanticsActions.RequestFocus)
        bar.performKeyInput { pressKey(Key.DirectionRight) }
        bar.performKeyInput { pressKey(Key.DirectionLeft) }

        assertEquals(listOf<PlayerIntent>(PlayerIntent.SeekBy(10_000), PlayerIntent.SeekBy(-10_000)), sent)
    }
}
