package com.example.livex.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.network.ThrottleMode
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkDropdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<ThrottleMode>()

    private fun setContent(mode: ThrottleMode = ThrottleMode.UNLIMITED) {
        composeRule.setContent {
            LiveXTheme { NetworkDropdown(mode = mode, onSelect = { selected += it }) }
        }
    }

    @Test
    fun collapsed_showsOnlyTheCurrentMode() {
        setContent(ThrottleMode.MBPS_2)

        composeRule.onNodeWithText("Network: 2 Mbps ▾").assertExists()
        composeRule.onNodeWithText("800 kbps").assertDoesNotExist()
    }

    @Test
    fun opening_listsEveryMode() {
        setContent()

        composeRule.onNodeWithText("Network: Unlimited ▾").performDpadClick()

        // Exact text: the header also contains "Unlimited". The current mode carries a check mark.
        ThrottleMode.entries.forEach { mode ->
            val text = if (mode == ThrottleMode.UNLIMITED) "✓ ${mode.label}" else mode.label
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun choosingAMode_reportsIt_andCollapses() {
        setContent()

        composeRule.onNodeWithText("Network: Unlimited ▾").performDpadClick()
        composeRule.onNodeWithText("800 kbps").performDpadClick()

        assertEquals(listOf(ThrottleMode.KBPS_800), selected)
        composeRule.onNodeWithText("Unstable", substring = true).assertDoesNotExist()
    }
}
