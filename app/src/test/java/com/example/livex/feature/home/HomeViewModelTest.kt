package com.example.livex.feature.home

import app.cash.turbine.test
import com.example.livex.data.SampleStreams
import com.example.livex.data.stream.FakeStreamRepository
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

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeStreamRepository

    @Before
    fun setUp() {
        repository = FakeStreamRepository()
    }

    private fun createViewModel() = HomeViewModel(repository)

    @Test
    fun `starts loading on creation`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `shows streams when load succeeds`() = runTest {
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(HomeState(streams = SampleStreams.all), viewModel.state.value)
    }

    @Test
    fun `shows error when load fails`() = runTest {
        repository.error = IOException("No network")
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(HomeState(error = "No network"), viewModel.state.value)
    }

    @Test
    fun `shows fallback error when exception has no message`() = runTest {
        repository.error = IOException()
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals("Something went wrong", viewModel.state.value.error)
    }

    @Test
    fun `retry after failure clears error and shows streams`() = runTest {
        repository.error = IOException("No network")
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.error = null
        viewModel.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        assertEquals(HomeState(streams = SampleStreams.all), viewModel.state.value)
        assertEquals(2, repository.callCount)
    }

    @Test
    fun `ignores load while already loading`() = runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(HomeIntent.Load)
        advanceUntilIdle()

        assertEquals(1, repository.callCount)
    }

    @Test
    fun `stream click emits navigate to player with the stream id`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HomeIntent.StreamClicked(SampleStreams.dashIfLivesim.id))

            assertEquals(HomeEffect.NavigateToPlayer(SampleStreams.dashIfLivesim.id), awaitItem())
        }
    }

    @Test
    fun `open sessions emits navigate to sessions`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HomeIntent.OpenSessions)

            assertEquals(HomeEffect.NavigateToSessions, awaitItem())
        }
    }

    @Test
    fun `open device info emits navigate to device info`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HomeIntent.OpenDeviceInfo)

            assertEquals(HomeEffect.NavigateToDeviceInfo, awaitItem())
        }
    }

    @Test
    fun `nothing is selected before the first click`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedStreamId)
    }

    @Test
    fun `stream click remembers the selection so focus can return to it after back`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.StreamClicked(SampleStreams.tearsOfSteelDash.id))

        assertEquals(SampleStreams.tearsOfSteelDash.id, viewModel.state.value.selectedStreamId)
    }

    @Test
    fun `retry keeps the remembered selection`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(HomeIntent.StreamClicked(SampleStreams.liveHls.id))

        viewModel.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        assertEquals(SampleStreams.liveHls.id, viewModel.state.value.selectedStreamId)
    }
}
