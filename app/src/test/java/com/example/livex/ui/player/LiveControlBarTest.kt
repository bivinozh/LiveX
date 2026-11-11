package com.example.livex.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.PlayerIntent
import com.example.livex.player.live.LiveLatency
import com.example.livex.player.live.LiveWindow
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
class LiveControlBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<PlayerIntent>()
    private var playPauseCount = 0

    private fun setContent(live: LiveWindow, latency: LiveLatency = LiveLatency.STANDARD, isPlaying: Boolean = true) {
        composeRule.setContent {
            LiveXTheme {
                LiveControlBar(
                    live = live,
                    latency = latency,
                    onIntent = { sent += it },
                    isPlaying = isPlaying,
                    onPlayPause = { playPauseCount++ },
                )
            }
        }
    }

    @Test
    fun pausedLive_showsPlay_andOkOnTheDvrBarToggles() {
        setContent(behind, isPlaying = false)

        composeRule.onNodeWithText("▶ Play").assertExists()
        val bar = composeRule.onNodeWithTag(DVR_BAR_TAG)
        bar.performSemanticsAction(SemanticsActions.RequestFocus)
        bar.performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, playPauseCount)
    }

    private val behind = LiveWindow(windowDurationMs = 60_000, positionMs = 30_000, defaultPositionMs = 50_000, targetOffsetMs = 10_000)

    @Test
    fun showsHowFarBehindLive_andGoLiveJumpsBack() {
        setContent(behind)

        composeRule.onNodeWithText("−20.0 s behind live").assertExists()
        composeRule.onNodeWithText("GO LIVE").performDpadClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.GoLive), sent)
    }

    @Test
    fun atLiveEdge_showsLiveBadge() {
        setContent(behind.copy(positionMs = 50_000))

        composeRule.onNodeWithText("● LIVE").assertExists()
    }

    @Test
    fun dvrBar_leftAndRightSeekTenSeconds() {
        setContent(behind)
        val bar = composeRule.onNodeWithTag(DVR_BAR_TAG)

        bar.performSemanticsAction(SemanticsActions.RequestFocus)
        bar.performKeyInput { pressKey(Key.DirectionLeft) }
        bar.performKeyInput { pressKey(Key.DirectionRight) }

        assertEquals(listOf<PlayerIntent>(PlayerIntent.SeekBy(-10_000), PlayerIntent.SeekBy(10_000)), sent)
    }

    @Test
    fun latencyButtons_markCurrent_andSendTheOtherChoice() {
        setContent(behind, latency = LiveLatency.STANDARD)

        composeRule.onNodeWithText("✓ 10 s").assertExists()
        composeRule.onNodeWithText("3 s").performDpadClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.SetLiveLatency(LiveLatency.LOW)), sent)
    }
}
