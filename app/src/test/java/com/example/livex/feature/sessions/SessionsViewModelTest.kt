package com.example.livex.feature.sessions

import com.example.livex.qoe.SessionSummary
import com.example.livex.qoe.store.FakeQoeSessionRepository
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = SessionSummary("s1", "bbb", "Big Buck Bunny", false, 1_000_000, 0, vstMs = 800)

    @Test
    fun `shows stored sessions`() = runTest {
        val viewModel = SessionsViewModel(FakeQoeSessionRepository(listOf(session)))

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf(session), viewModel.state.value.sessions)
    }

    @Test
    fun `clear empties the list`() = runTest {
        val repository = FakeQoeSessionRepository(listOf(session))
        val viewModel = SessionsViewModel(repository)
        advanceUntilIdle()

        viewModel.onIntent(SessionsIntent.Clear)
        advanceUntilIdle()

        assertEquals(1, repository.clearCount)
        assertTrue(viewModel.state.value.sessions.isEmpty())
    }
}
