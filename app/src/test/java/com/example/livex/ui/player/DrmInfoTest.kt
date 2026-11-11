package com.example.livex.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.data.SampleStreams
import com.example.livex.player.PlayerState
import com.example.livex.player.drm.WidevineInfo
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The guide tells the viewer which DRM protects the stream and what this device can do. */
@RunWith(AndroidJUnit4::class)
class DrmInfoTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun protectedStream_showsDeviceSecurityLevel() {
        composeRule.setContent {
            LiveXTheme {
                GuideOverlay(
                    state = PlayerState(stream = SampleStreams.widevineHd, widevine = WidevineInfo.Available("L3", null, null)),
                    onIntent = {},
                )
            }
        }

        composeRule.onNodeWithText("🔒 Widevine L3 (software) · usually SD only").assertExists()
    }

    @Test
    fun clearStream_showsNoDrmLine() {
        composeRule.setContent {
            LiveXTheme {
                GuideOverlay(
                    state = PlayerState(stream = SampleStreams.bigBuckBunny, widevine = WidevineInfo.Available("L3", null, null)),
                    onIntent = {},
                )
            }
        }

        composeRule.onNodeWithText("Widevine", substring = true).assertDoesNotExist()
    }
}
