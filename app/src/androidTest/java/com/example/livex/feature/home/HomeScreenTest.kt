package com.example.livex.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import com.example.livex.HiltTestActivity
import com.example.livex.di.FAKE_STREAM
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Home screen on a device with real Hilt wiring; the repository is replaced by a Hilt fake. */
@HiltAndroidTest
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listsStreamsFromRepository_andSelectingOneRequestsNavigation() {
        var selectedId: String? = null
        composeRule.setContent { HomeScreen(onStreamSelected = { selectedId = it }) }

        composeRule.waitUntilExactlyOneExists(hasText(FAKE_STREAM.title, substring = true), timeoutMillis = 5_000)
        // The first item is focused automatically; D-pad center "clicks" it.
        composeRule.onNodeWithText(FAKE_STREAM.title, substring = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitForIdle()
        assertEquals(FAKE_STREAM.id, selectedId)
    }
}
