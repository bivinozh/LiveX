package com.example.livex.feature.home

import app.cash.turbine.test
import com.example.livex.data.greeting.FakeGreetingRepository
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeGreetingRepository

    @Before
    fun setUp() {
        repository = FakeGreetingRepository()
    }

    private fun createViewModel() = HomeViewModel(repository)

    @Test
    fun `starts loading on creation`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `shows greeting when load succeeds`() = runTest {
        repository.greeting = "Hello TDD!"
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(HomeState(greeting = "Hello TDD!"), viewModel.state.value)
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
    fun `retry after failure clears error and shows greeting`() = runTest {
        repository.error = IOException("No network")
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.error = null
        viewModel.onIntent(HomeIntent.Retry)
        advanceUntilIdle()

        assertEquals(HomeState(greeting = "Hello Test!"), viewModel.state.value)
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
    fun `greeting click emits welcome message effect`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(HomeIntent.GreetingClicked)

            assertEquals(HomeEffect.ShowMessage("Welcome to LiveX!"), awaitItem())
        }
    }
}
