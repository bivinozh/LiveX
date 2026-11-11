package com.example.livex.device

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.player.drm.WidevineInfo
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land")
class DeviceInfoContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<DeviceInfoIntent>()

    private val info = DeviceInfo(
        model = "Google TV Streamer",
        sdkInt = 36,
        isTelevision = true,
        codecs = CodecSupport.merge(
            listOf(
                DecoderEntry(VideoCodec.AVC, "avc", isHardware = true, supports4k = false),
                DecoderEntry(VideoCodec.HEVC, "hevc", isHardware = true, supports4k = true, setOf(HdrFormat.HDR10)),
            ),
        ),
        display = DisplayInfo(1920, 1080, 60f, 3840, 2160, setOf(HdrFormat.HDR10)),
        memory = MemoryInfo(4096, 256, isLowRam = false),
        widevine = WidevineInfo.Available("L1", "HDCP-2.2", "1"),
    )

    private fun setContent(state: DeviceInfoState) = composeRule.setContent {
        LiveXTheme { DeviceInfoContent(state = state, onIntent = { sent += it }) }
    }

    @Test
    fun showsCapabilitySummaryAndDecoders() {
        setContent(DeviceInfoState(isLoading = false, info = info))

        composeRule.onNodeWithText("Google TV Streamer · Android API 36 · TV").assertIsDisplayed()
        composeRule.onNodeWithText("4K (2160p)").assertIsDisplayed()
        composeRule.onNodeWithText("HW · 4K · HDR10").assertExists()
        composeRule.onNodeWithText("Widevine L1 (hardware) · HD/UHD OK").assertExists()
    }

    @Test
    fun loadingAndError() {
        setContent(DeviceInfoState(isLoading = false, error = "boom"))

        composeRule.onNodeWithText("boom").assertIsDisplayed()
    }

    @Test
    fun refreshSendsRefresh() {
        setContent(DeviceInfoState(isLoading = false, info = info))

        composeRule.onNodeWithText("Refresh").performDpadClick()

        assertEquals(listOf<DeviceInfoIntent>(DeviceInfoIntent.Refresh), sent)
    }
}
