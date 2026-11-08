package com.example.livex.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.data.SampleStreams
import com.example.livex.data.Topics
import com.example.livex.player.PlayerConfig
import com.example.livex.player.PlayerIntent
import com.example.livex.player.PlayerState
import com.example.livex.qoe.QoeMetrics
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sentIntents = mutableListOf<PlayerIntent>()
    private var state by mutableStateOf(PlayerState())

    private fun setContent(initial: PlayerState) {
        state = initial
        composeRule.setContent {
            LiveXTheme { GuideOverlay(state = state, onIntent = { sentIntents += it }) }
        }
    }

    private val bufferTopicIndex = Topics.all.indexOf(Topics.bufferTuning)

    @Test
    fun showsTopicTitleDescriptionAndGuidance() {
        setContent(PlayerState())

        composeRule.onNodeWithText(Topics.hlsBasics.title).assertIsDisplayed()
        composeRule.onNodeWithText(Topics.hlsBasics.description).assertExists()
        composeRule.onNodeWithText("• ${Topics.hlsBasics.guidance.first()}").assertExists()
    }

    @Test
    fun topicWithoutOptimization_hasNoToggle() {
        setContent(PlayerState())

        composeRule.onNodeWithText("Optimized: OFF").assertDoesNotExist()
    }

    @Test
    fun optimizationTopic_showsModeAndToggleSendsIntent() {
        setContent(PlayerState(topicIndex = bufferTopicIndex))

        composeRule.onNodeWithText("Now: ${Topics.bufferTuning.optimization!!.defaultLabel}").assertExists()
        composeRule.onNodeWithText("Optimized: OFF").performDpadClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ToggleOptimization), sentIntents)
    }

    @Test
    fun enabledOptimization_showsOptimizedMode() {
        setContent(PlayerState(topicIndex = bufferTopicIndex, config = PlayerConfig(tunedBuffer = true)))

        composeRule.onNodeWithText("Optimized: ON").assertExists()
        composeRule.onNodeWithText("Now: ${Topics.bufferTuning.optimization!!.optimizedLabel}").assertExists()
    }

    @Test
    fun liveMetricsAreDisplayed() {
        setContent(PlayerState(topicIndex = bufferTopicIndex, metrics = QoeMetrics(startupTimeMs = 840)))

        composeRule.onNodeWithText("840 ms").assertExists()
    }

    @Test
    fun navigationButtonsSendIntents() {
        setContent(PlayerState())

        composeRule.onNodeWithText("Next ▶").performDpadClick()
        composeRule.onNodeWithText("◀ Prev").performDpadClick()
        composeRule.onNodeWithText("Hide").performDpadClick()

        assertEquals(
            listOf(PlayerIntent.NextTopic, PlayerIntent.PreviousTopic, PlayerIntent.ToggleGuide),
            sentIntents,
        )
    }

    @Test
    fun showsCurrentVideo_andVideoButtonsSendIntents() {
        setContent(PlayerState())

        composeRule.onNodeWithText(SampleStreams.all.first().title).assertIsDisplayed()
        composeRule.onNodeWithText("Video ▶").performDpadClick()
        composeRule.onNodeWithText("◀ Video").performDpadClick()

        assertEquals(listOf(PlayerIntent.NextStream, PlayerIntent.PreviousStream), sentIntents)
    }

    @Test
    fun hiddenGuide_showsOnlyShowGuideButton() {
        setContent(PlayerState(isGuideVisible = false))

        composeRule.onNodeWithText(Topics.hlsBasics.title).assertDoesNotExist()
        composeRule.onNodeWithText("Show guide").performDpadClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ToggleGuide), sentIntents)
    }
}
