package com.example.livex.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.PlayerIntent
import com.example.livex.player.PlayerState
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Back on TV: dismiss the overlay first, leave the player second. Needs a real Activity's back dispatcher. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land")
class GuideBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<PlayerIntent>()

    private fun setContent(state: PlayerState) = composeRule.setContent {
        LiveXTheme { GuideOverlay(state = state, onIntent = { sent += it }) }
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backWithTheGuideOpen_hidesTheGuide() {
        setContent(PlayerState(isGuideVisible = true))

        pressBack()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ToggleGuide), sent)
    }

    @Test
    fun backWithTheGuideHidden_isLeftToNavigation() {
        // Stands in for the NavHost's handler. Registered first, like the NavHost (which composes
        // before the player screen): later callbacks get Back first, so ours must be disabled to pass it on.
        var navigationGotBack = false
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.addCallback(composeRule.activity) { navigationGotBack = true }
        }
        setContent(PlayerState(isGuideVisible = false))

        pressBack()

        assertEquals(emptyList<PlayerIntent>(), sent)
        assertTrue(navigationGotBack)
    }
}
