package com.example.livex.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun loadedState_showsGreeting_andClickSendsGreetingClicked() {
        setContent(HomeState(greeting = "Hello TDD!"))

        composeRule.onNodeWithText("Hello TDD!").assertIsDisplayed().performDpadClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.GreetingClicked), sentIntents)
    }

    @Test
    fun errorState_showsMessage_andRetrySendsRetry() {
        setContent(HomeState(error = "No network"))

        composeRule.onNodeWithText("No network").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performDpadClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), sentIntents)
    }
}
