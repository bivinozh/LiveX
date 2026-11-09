package com.example.livex.settings

import app.cash.turbine.test
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BufferSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBufferSettingsRepository(BufferPreset.STABLE_VOD.settings)

    private fun loadedViewModel() = BufferSettingsViewModel(repository)

    @Test
    fun `loads the saved settings into the editor`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(BufferPreset.STABLE_VOD.settings, state.draft)
        assertEquals(BufferPreset.STABLE_VOD, state.matchingPreset)
        assertFalse(state.isDirty)
    }

    @Test
    fun `moving a slider edits the draft only`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.onIntent(BufferSettingsIntent.Change(BufferField.FOR_PLAYBACK, 1_500))

        val state = viewModel.state.value
        assertEquals(1_500, state.draft.bufferForPlaybackMs)
        assertTrue(state.isDirty)
        assertEquals(null, state.matchingPreset)
        assertTrue("not saved yet", repository.saved.isEmpty())
    }

    @Test
    fun `slider changes keep the settings valid`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.onIntent(BufferSettingsIntent.Change(BufferField.MAX_BUFFER, 2_000))

        val draft = viewModel.state.value.draft
        assertEquals(2_000, draft.maxBufferMs)
        assertTrue(draft.minBufferMs <= 2_000)
    }

    @Test
    fun `choosing a preset fills all four values`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.onIntent(BufferSettingsIntent.ApplyPreset(BufferPreset.LOW_LATENCY_LIVE))

        assertEquals(BufferPreset.LOW_LATENCY_LIVE.settings, viewModel.state.value.draft)
        assertEquals(BufferPreset.LOW_LATENCY_LIVE, viewModel.state.value.matchingPreset)
    }

    @Test
    fun `reset puts back the Media3 defaults`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.onIntent(BufferSettingsIntent.ResetToMedia3Default)

        assertEquals(BufferSettings.MEDIA3_DEFAULT, viewModel.state.value.draft)
    }

    @Test
    fun `save writes to DataStore and reports success`() = runTest {
        val viewModel = loadedViewModel()
        advanceUntilIdle()
        viewModel.onIntent(BufferSettingsIntent.ApplyPreset(BufferPreset.FAST_START))

        viewModel.effects.test {
            viewModel.onIntent(BufferSettingsIntent.Save)
            advanceUntilIdle()

            assertEquals(BufferSettingsEffect.Saved, awaitItem())
        }
        assertEquals(listOf(BufferPreset.FAST_START.settings), repository.saved)
        assertFalse(viewModel.state.value.isDirty)
    }
}
