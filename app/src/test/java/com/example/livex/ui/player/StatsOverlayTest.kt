package com.example.livex.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.qoe.StatsState
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEveryStatWithItsValue() {
        val stats = StatsState(
            playerState = "Buffering",
            videoWidth = 1920,
            videoHeight = 1080,
            liveOffsetMs = 8_000,
            droppedFrames = 7,
        )
        composeRule.setContent { LiveXTheme { StatsOverlay(stats) } }

        stats.rows().forEach { row ->
            composeRule.onNodeWithText(row.label).assertExists()
        }
        composeRule.onNodeWithText("Buffering").assertExists()
        composeRule.onNodeWithText("1920×1080").assertExists()
        composeRule.onNodeWithText("8.0 s").assertExists()
        composeRule.onNodeWithText("7").assertExists()
    }
}
