package com.example.livex.manifest

import androidx.lifecycle.SavedStateHandle
import com.example.livex.data.SampleStreams
import com.example.livex.data.manifest.FakeManifestRepository
import com.example.livex.navigation.ManifestRoute
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ManifestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stream = SampleStreams.akamaiLive
    private val variantUrl = stream.url.substringBeforeLast('/') + "/low/index.m3u8"
    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        low/index.m3u8
    """.trimIndent()
    private fun media(sequence: Int) = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:$sequence
        #EXTINF:6.0,
        seg$sequence.ts
    """.trimIndent()

    private lateinit var repository: FakeManifestRepository

    @Before
    fun setUp() {
        repository = FakeManifestRepository(mutableMapOf(stream.url to master, variantUrl to media(100)))
    }

    private fun createViewModel(streamId: String = stream.id) =
        ManifestViewModel(repository, SavedStateHandle(mapOf(ManifestViewModel.KEY_STREAM_ID to streamId)))

    @Test
    fun `stream id key matches the navigation route property`() {
        assertEquals(ManifestViewModel.KEY_STREAM_ID, ManifestRoute::streamId.name)
    }

    @Test
    fun `loads the stream's manifest on start`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.state.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(stream.url), repository.requestedUrls)
        assertEquals(master, state.text)
        assertEquals(ManifestSummary.HlsMaster(variantCount = 1), state.summary)
        assertTrue(state.highlights.isNotEmpty())
        assertTrue(state.isViewingMaster)
    }

    @Test
    fun `master playlist exposes its variants`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(HlsVariant("640x360 · 800 kbps", variantUrl)), viewModel.state.value.variants)
    }

    @Test
    fun `selecting a variant loads its media playlist and keeps the variant list`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ManifestIntent.SelectVariant(0))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(variantUrl, state.currentUrl)
        assertEquals(media(100), state.text)
        assertEquals(1, state.variants.size)
        assertEquals(100L, (state.summary as ManifestSummary.HlsMedia).mediaSequence)
    }

    @Test
    fun `refresh refetches the current playlist and shows the new version`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(ManifestIntent.SelectVariant(0))
        advanceUntilIdle()

        repository.responses[variantUrl] = media(101) // the live window moved on
        viewModel.onIntent(ManifestIntent.Refresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(stream.url, variantUrl, variantUrl), repository.requestedUrls)
        assertEquals(101L, (state.summary as ManifestSummary.HlsMedia).mediaSequence)
        assertEquals(3, state.fetchCount)
    }

    @Test
    fun `select master goes back to the master playlist`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(ManifestIntent.SelectVariant(0))
        advanceUntilIdle()

        viewModel.onIntent(ManifestIntent.SelectMaster)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isViewingMaster)
        assertEquals(master, viewModel.state.value.text)
    }

    @Test
    fun `download failure shows the error`() = runTest {
        repository.error = IOException("HTTP 503")
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals("HTTP 503", viewModel.state.value.error)
        assertNull(viewModel.state.value.text)
    }

    @Test
    fun `unknown stream id falls back to the first stream`() = runTest {
        repository.responses[SampleStreams.all.first().url] = master
        val viewModel = createViewModel(streamId = "nope")

        assertEquals(SampleStreams.all.first(), viewModel.state.value.stream)
    }
}
