package com.example.livex.player

import app.cash.turbine.test
import com.example.livex.data.SampleStreams
import com.example.livex.data.Topics
import com.example.livex.qoe.QoeMetrics
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var engine: FakePlaybackEngine
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        engine = FakePlaybackEngine()
        viewModel = PlayerViewModel(engine)
    }

    private fun goToTopic(index: Int) = repeat(index) { viewModel.onIntent(PlayerIntent.NextTopic) }

    @Test
    fun `starts playback with default config on creation`() {
        assertEquals(listOf(PlayerConfig()), engine.startedConfigs)
        assertEquals(listOf(SampleStreams.all.first()), engine.startedStreams)
    }

    @Test
    fun `next stream starts the next video`() {
        viewModel.onIntent(PlayerIntent.NextStream)

        assertEquals(1, viewModel.state.value.streamIndex)
        assertEquals(SampleStreams.all[1], engine.startedStreams.last())
    }

    @Test
    fun `previous stream wraps to the last video`() {
        viewModel.onIntent(PlayerIntent.PreviousStream)

        assertEquals(SampleStreams.all.lastIndex, viewModel.state.value.streamIndex)
        assertEquals(SampleStreams.all.last(), engine.startedStreams.last())
    }

    @Test
    fun `switching video keeps the enabled optimizations`() {
        goToTopic(Topics.all.indexOf(Topics.bufferTuning))
        viewModel.onIntent(PlayerIntent.ToggleOptimization)

        viewModel.onIntent(PlayerIntent.NextStream)

        assertEquals(PlayerConfig(tunedBuffer = true), engine.startedConfigs.last())
    }

    @Test
    fun `next and previous topic wrap around`() {
        viewModel.onIntent(PlayerIntent.PreviousTopic)
        assertEquals(Topics.all.lastIndex, viewModel.state.value.topicIndex)

        viewModel.onIntent(PlayerIntent.NextTopic)
        assertEquals(0, viewModel.state.value.topicIndex)
    }

    @Test
    fun `toggle on a topic without optimization does nothing`() {
        viewModel.onIntent(PlayerIntent.ToggleOptimization) // topic 0: HLS basics

        assertEquals(PlayerConfig(), viewModel.state.value.config)
        assertEquals(1, engine.startedConfigs.size)
    }

    @Test
    fun `toggle on buffer topic restarts player with tuned buffer`() {
        goToTopic(Topics.all.indexOf(Topics.bufferTuning))

        viewModel.onIntent(PlayerIntent.ToggleOptimization)

        val expected = PlayerConfig(tunedBuffer = true)
        assertEquals(expected, viewModel.state.value.config)
        assertEquals(expected, engine.startedConfigs.last())
        assertTrue(viewModel.state.value.isOptimizationEnabled)
    }

    @Test
    fun `optimizations combine across topics`() {
        goToTopic(Topics.all.indexOf(Topics.abr))
        viewModel.onIntent(PlayerIntent.ToggleOptimization)
        viewModel.onIntent(PlayerIntent.NextTopic) // network
        viewModel.onIntent(PlayerIntent.ToggleOptimization)

        assertEquals(PlayerConfig(dataSaverAbr = true, okHttpNetwork = true), engine.startedConfigs.last())
    }

    @Test
    fun `toggle emits restart message with the new mode`() = runTest {
        goToTopic(Topics.all.indexOf(Topics.network))

        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.ToggleOptimization)

            val message = (awaitItem() as PlayerEffect.ShowMessage).message
            assertTrue(message.contains(Topics.network.optimization!!.optimizedLabel))
        }
    }

    @Test
    fun `engine metrics flow into state`() = runTest {
        engine.metrics.value = QoeMetrics(startupTimeMs = 720)

        advanceUntilIdle()

        assertEquals(720L, viewModel.state.value.metrics.startupTimeMs)
    }

    @Test
    fun `toggle guide hides and shows the overlay`() {
        viewModel.onIntent(PlayerIntent.ToggleGuide)
        assertFalse(viewModel.state.value.isGuideVisible)

        viewModel.onIntent(PlayerIntent.ToggleGuide)
        assertTrue(viewModel.state.value.isGuideVisible)
    }
}
