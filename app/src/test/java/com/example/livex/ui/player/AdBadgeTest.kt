package com.example.livex.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.ads.AdBreakKind
import com.example.livex.player.ads.AdState
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAdBadgeWhilePlayingAnAd() {
        val ad = AdState(isPlayingAd = true, breakKind = AdBreakKind.PRE_ROLL, adIndexInBreak = 0, adsInBreak = 1, remainingMs = 9_000)
        composeRule.setContent { LiveXTheme { AdBadge(ad) } }

        composeRule.onNodeWithText("AD 1/1 · 9 s").assertExists()
    }

    @Test
    fun showsNothingDuringContent() {
        composeRule.setContent { LiveXTheme { AdBadge(AdState()) } }

        composeRule.onNodeWithText("AD", substring = true).assertDoesNotExist()
    }
}
