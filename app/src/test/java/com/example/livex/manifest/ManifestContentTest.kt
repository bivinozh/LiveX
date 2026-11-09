package com.example.livex.manifest

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.data.SampleStreams
import com.example.livex.testutil.performDpadClick
import com.example.livex.ui.theme.LiveXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sentIntents = mutableListOf<ManifestIntent>()

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        low/index.m3u8
    """.trimIndent()

    private fun setContent(state: ManifestState) {
        composeRule.setContent {
            LiveXTheme { ManifestContent(state = state, onIntent = { sentIntents += it }) }
        }
    }

    private fun loadedState(stream: com.example.livex.data.Stream) = ManifestState(
        stream = stream,
        currentUrl = stream.url,
        text = master,
        highlights = ManifestAnalyzer.highlights(master),
        summary = ManifestAnalyzer.summarize(master),
        variants = ManifestAnalyzer.hlsVariants(master, stream.url),
        fetchCount = 1,
    )

    @Test
    fun showsRawManifestTextAndSummary() {
        setContent(loadedState(SampleStreams.bigBuckBunny))

        composeRule.onNodeWithText("low/index.m3u8", substring = true).assertExists()
        composeRule.onNodeWithText("HLS master playlist", substring = true).assertIsDisplayed()
    }

    @Test
    fun vodStream_hasNoRefreshButton() {
        setContent(loadedState(SampleStreams.bigBuckBunny))

        composeRule.onNodeWithText("Refresh").assertDoesNotExist()
    }

    @Test
    fun liveStream_refreshSendsRefresh() {
        setContent(loadedState(SampleStreams.liveHls))

        composeRule.onNodeWithText("Refresh").performDpadClick()

        assertEquals(listOf<ManifestIntent>(ManifestIntent.Refresh), sentIntents)
    }

    @Test
    fun variantButtonsSendSelectVariant() {
        setContent(loadedState(SampleStreams.bigBuckBunny))

        composeRule.onNodeWithText("640x360 · 800 kbps").performDpadClick()

        assertEquals(listOf<ManifestIntent>(ManifestIntent.SelectVariant(0)), sentIntents)
    }

    @Test
    fun errorIsShown() {
        setContent(ManifestState(stream = SampleStreams.bigBuckBunny, currentUrl = "u", error = "HTTP 503"))

        composeRule.onNodeWithText("HTTP 503", substring = true).assertIsDisplayed()
    }
}
