package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.livex.data.SampleStreams
import com.example.livex.data.Topics
import com.example.livex.qoe.QoeMetrics
import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var engine: FakePlaybackEngine
    private lateinit var savedState: SavedStateHandle
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        engine = FakePlaybackEngine()
        savedState = SavedStateHandle()
        viewModel = PlayerViewModel(engine, savedState)
    }

    /** Simulates a new ViewModel after process death, restored from [saved]. */
    private fun recreate(saved: Map<String, Any?>): PlayerViewModel {
        engine = FakePlaybackEngine()
        return PlayerViewModel(engine, SavedStateHandle(saved))
    }

    // --- Lifecycle ---

    @Test
    fun `app stop pauses playback`() {
        viewModel.onIntent(PlayerIntent.LifecycleStop)

        assertEquals(1, engine.pauseCount)
    }

    @Test
    fun `app start resumes playback`() {
        viewModel.onIntent(PlayerIntent.LifecycleStop)
        viewModel.onIntent(PlayerIntent.LifecycleStart)

        assertEquals(1, engine.resumeCount)
    }

    @Test
    fun `app stop saves position stream and optimizations`() {
        viewModel.onIntent(PlayerIntent.NextStream)
        goToTopic(Topics.all.indexOf(Topics.bufferTuning))
        viewModel.onIntent(PlayerIntent.ToggleOptimization)
        engine.currentPositionMs = 42_000

        viewModel.onIntent(PlayerIntent.LifecycleStop)

        assertEquals(42_000L, savedState.get<Long>(PlayerViewModel.KEY_POSITION_MS))
        assertEquals(1, savedState.get<Int>(PlayerViewModel.KEY_STREAM_INDEX))
        assertEquals(true, savedState.get<Boolean>(PlayerViewModel.KEY_TUNED_BUFFER))
    }

    @Test
    fun `fresh start has no start position`() {
        assertNull(engine.startPositions.single())
    }

    @Test
    fun `restores stream position and optimizations after process death`() {
        val restored = recreate(
            mapOf(
                PlayerViewModel.KEY_STREAM_INDEX to 1,
                PlayerViewModel.KEY_POSITION_MS to 42_000L,
                PlayerViewModel.KEY_TUNED_BUFFER to true,
            ),
        )

        assertEquals(1, restored.state.value.streamIndex)
        assertEquals(SampleStreams.all[1], engine.startedStreams.single())
        assertEquals(42_000L, engine.startPositions.single())
        assertEquals(PlayerConfig(tunedBuffer = true), engine.startedConfigs.single())
    }

    @Test
    fun `live stream is not restored to an old position`() {
        val liveIndex = SampleStreams.all.indexOfFirst { it.isLive }

        recreate(
            mapOf(
                PlayerViewModel.KEY_STREAM_INDEX to liveIndex,
                PlayerViewModel.KEY_POSITION_MS to 42_000L,
            ),
        )

        assertNull(engine.startPositions.single())
    }

    @Test
    fun `out of range saved stream index falls back to first video`() {
        val restored = recreate(mapOf(PlayerViewModel.KEY_STREAM_INDEX to 99))

        assertEquals(0, restored.state.value.streamIndex)
    }

    // --- Topics, streams, optimizations ---

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
