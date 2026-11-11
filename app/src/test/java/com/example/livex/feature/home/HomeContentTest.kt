package com.example.livex.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.data.SampleStreams
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI behavior tests for the stateless [HomeContent], run on the JVM with Robolectric. */
@RunWith(AndroidJUnit4::class)
class HomeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sentIntents = mutableListOf<HomeIntent>()

    private fun setContent(state: HomeState) {
        composeRule.setContent {
            LiveXTheme {
                HomeContent(state = state, onIntent = { sentIntents += it })
            }
        }
    }

    @Test
    fun loadingState_showsLoadingText() {
        setContent(HomeState(isLoading = true))

        composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun loadedState_listsStreamsWithFormatAndLiveLabel() {
        setContent(HomeState(streams = listOf(SampleStreams.bigBuckBunny, SampleStreams.dashIfLivesim)))

        composeRule.onNodeWithText(SampleStreams.bigBuckBunny.title, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("HLS · VOD", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(SampleStreams.dashIfLivesim.title, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("DASH · Live", substring = true).assertIsDisplayed()
    }

    @Test
    fun clickingAStream_sendsStreamClickedWithItsId() {
        setContent(HomeState(streams = SampleStreams.all))

        composeRule.onNodeWithText(SampleStreams.tearsOfSteelDash.title, substring = true).performDpadClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.StreamClicked(SampleStreams.tearsOfSteelDash.id)), sentIntents)
    }

    @Test
    fun sessionsButton_sendsOpenSessions() {
        setContent(HomeState(streams = SampleStreams.all))

        composeRule.onNodeWithText("QoE sessions").performDpadClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenSessions), sentIntents)
    }

    @Test
    fun noSelection_focusesFirstStream() {
        setContent(HomeState(streams = SampleStreams.all))

        composeRule.onNodeWithText(SampleStreams.all.first().title, substring = true).assertIsFocused()
    }

    @Test
    fun returningWithSelection_restoresFocusOnThatStream() {
        setContent(HomeState(streams = SampleStreams.all, selectedStreamId = SampleStreams.liveHls.id))

        composeRule.onNodeWithText(SampleStreams.liveHls.title, substring = true).assertIsFocused()
        composeRule.onNodeWithText(SampleStreams.all.first().title, substring = true).assertIsNotFocused()
    }

    @Test
    fun unknownSelection_fallsBackToFirstStream() {
        setContent(HomeState(streams = SampleStreams.all, selectedStreamId = "removed-stream"))

        composeRule.onNodeWithText(SampleStreams.all.first().title, substring = true).assertIsFocused()
    }

    @Test
    fun errorState_showsMessage_andRetrySendsRetry() {
        setContent(HomeState(error = "No network"))

        composeRule.onNodeWithText("No network").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performDpadClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), sentIntents)
    }
}
