package com.example.livex.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.livex.MainActivity
import com.example.livex.di.FAKE_GREETING
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/** End-to-end Home screen test on a device, with the repository replaced by a Hilt fake. */
@HiltAndroidTest
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun launch_showsGreetingFromRepository() {
        composeRule.waitUntilExactlyOneExists(hasText(FAKE_GREETING), timeoutMillis = 5_000)
        composeRule.onNodeWithText(FAKE_GREETING).assertIsDisplayed()
    }
}
