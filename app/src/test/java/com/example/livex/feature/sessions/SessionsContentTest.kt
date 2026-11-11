package com.example.livex.feature.sessions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.qoe.EndReason
import com.example.livex.qoe.SessionSummary
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land")
class SessionsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<SessionsIntent>()

    private fun setContent(state: SessionsState) = composeRule.setContent {
        LiveXTheme { SessionsContent(state = state, onIntent = { sent += it }, zone = ZoneOffset.UTC) }
    }

    private val played = SessionSummary(
        "a", "bbb", "Big Buck Bunny", false, 1_700_000_000_000, 0, durationMs = 60_000, vstMs = 900,
        stallCount = 1, stallTimeMs = 1_000, playingTimeMs = 59_000, avgBitrateBps = 2_160_000,
        endReason = EndReason.COMPLETED,
    )
    private val failed = SessionSummary(
        "b", "wv", "Tears of Steel (Widevine HD)", false, 1_699_999_000_000, 0, durationMs = 3_000,
        failures = 1, lastError = "Decryption failed", endReason = EndReason.EXIT,
    )

    @Test
    fun `each session shows its QoE summary`() {
        setContent(SessionsState(isLoading = false, sessions = listOf(played, failed)))

        composeRule.onNodeWithText("Big Buck Bunny").assertIsDisplayed()
        composeRule.onNodeWithText("Nov 14, 22:13 · 1:00 · Completed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "VST 900 ms · Rebuffering 1.7 % · Stalls 1 · Avg 2.2 Mbps · Switches 0 · Failures 0",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Last error: Decryption failed").assertIsDisplayed()
    }

    @Test
    fun `empty state explains what to do`() {
        setContent(SessionsState(isLoading = false))

        composeRule.onNodeWithText("No sessions yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `clear sends Clear`() {
        setContent(SessionsState(isLoading = false, sessions = listOf(played)))

        composeRule.onNodeWithText("Clear").performDpadClick()

        assertEquals(listOf<SessionsIntent>(SessionsIntent.Clear), sent)
    }
}
