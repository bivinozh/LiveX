package com.example.livex.player

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.livex.data.SampleStreams
import com.example.livex.data.Topics
import com.example.livex.navigation.PlayerRoute
import com.example.livex.network.ThrottleMode
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.StallStats
import com.example.livex.qoe.StatsState
import com.example.livex.settings.BufferPreset
import com.example.livex.settings.FakeBufferSettingsRepository
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
    private lateinit var settings: FakeBufferSettingsRepository
    private lateinit var savedState: SavedStateHandle
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        engine = FakePlaybackEngine()
        settings = FakeBufferSettingsRepository()
        savedState = SavedStateHandle(mapOf(PlayerViewModel.KEY_STREAM_ID to SampleStreams.bigBuckBunny.id))
        viewModel = PlayerViewModel(engine, settings, savedState)
    }

    /** A new ViewModel created from [saved] (navigation argument and/or state restored after process death). */
    private fun create(saved: Map<String, Any?>): PlayerViewModel {
        engine = FakePlaybackEngine()
        return PlayerViewModel(engine, settings, SavedStateHandle(saved))
    }

    private fun goToTopic(index: Int) = repeat(index) { viewModel.onIntent(PlayerIntent.NextTopic) }

    // --- Navigation argument ---

    @Test
    fun `stream id key matches the navigation route property`() {
        assertEquals(PlayerViewModel.KEY_STREAM_ID, PlayerRoute::streamId.name)
    }

    @Test
    fun `plays the stream passed as navigation argument`() {
        val vm = create(mapOf(PlayerViewModel.KEY_STREAM_ID to SampleStreams.dashIfLivesim.id))

        assertEquals(SampleStreams.dashIfLivesim, vm.state.value.stream)
        assertEquals(SampleStreams.dashIfLivesim, engine.startedStreams.single())
    }

    @Test
    fun `unknown stream id falls back to the first stream`() {
        val vm = create(mapOf(PlayerViewModel.KEY_STREAM_ID to "does-not-exist"))

        assertEquals(SampleStreams.all.first(), vm.state.value.stream)
    }

    @Test
    fun `starts playback with default config on creation`() {
        assertEquals(listOf(PlayerConfig()), engine.startedConfigs)
        assertTrue("OkHttp is the default network stack", engine.startedConfigs.single().okHttpNetwork)
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
    fun `app stop saves position and optimizations`() {
        goToTopic(Topics.all.indexOf(Topics.bufferTuning))
        viewModel.onIntent(PlayerIntent.ToggleOptimization)
        engine.currentPositionMs = 42_000

        viewModel.onIntent(PlayerIntent.LifecycleStop)

        assertEquals(42_000L, savedState.get<Long>(PlayerViewModel.KEY_POSITION_MS))
        assertEquals(true, savedState.get<Boolean>(PlayerViewModel.KEY_TUNED_BUFFER))
        assertEquals(true, savedState.get<Boolean>(PlayerViewModel.KEY_OKHTTP_NETWORK))
    }

    @Test
    fun `fresh start has no start position`() {
        assertNull(engine.startPositions.single())
    }

    @Test
    fun `restores position and optimizations after process death`() {
        create(
            mapOf(
                PlayerViewModel.KEY_STREAM_ID to SampleStreams.tearsOfSteelDash.id,
                PlayerViewModel.KEY_POSITION_MS to 42_000L,
                PlayerViewModel.KEY_TUNED_BUFFER to true,
                PlayerViewModel.KEY_OKHTTP_NETWORK to false,
            ),
        )

        assertEquals(SampleStreams.tearsOfSteelDash, engine.startedStreams.single())
        assertEquals(42_000L, engine.startPositions.single())
        assertEquals(PlayerConfig(tunedBuffer = true, okHttpNetwork = false), engine.startedConfigs.single())
    }

    @Test
    fun `live stream is not restored to an old position`() {
        create(
            mapOf(
                PlayerViewModel.KEY_STREAM_ID to SampleStreams.liveHls.id,
                PlayerViewModel.KEY_POSITION_MS to 42_000L,
            ),
        )

        assertNull(engine.startPositions.single())
    }

    // --- Topics and optimizations ---

    @Test
    fun `next and previous topic wrap around`() {
        viewModel.onIntent(PlayerIntent.PreviousTopic)
        assertEquals(Topics.all.lastIndex, viewModel.state.value.topicIndex)

        viewModel.onIntent(PlayerIntent.NextTopic)
        assertEquals(0, viewModel.state.value.topicIndex)
    }

    @Test
    fun `toggle on a topic without optimization does nothing`() {
        viewModel.onIntent(PlayerIntent.ToggleOptimization) // topic 0: playback basics

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
        viewModel.onIntent(PlayerIntent.ToggleOptimization) // ABR data saver on
        viewModel.onIntent(PlayerIntent.NextTopic) // network
        viewModel.onIntent(PlayerIntent.ToggleOptimization) // OkHttp off -> platform stack

        assertEquals(PlayerConfig(dataSaverAbr = true, okHttpNetwork = false), engine.startedConfigs.last())
    }

    @Test
    fun `toggle emits restart message with the new mode`() = runTest {
        goToTopic(Topics.all.indexOf(Topics.network))

        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.ToggleOptimization) // OkHttp (default) -> platform

            val message = (awaitItem() as PlayerEffect.ShowMessage).message
            assertTrue(message.contains(Topics.network.optimization!!.defaultLabel))
        }
    }

    @Test
    fun `engine metrics flow into state`() = runTest {
        engine.metrics.value = QoeMetrics(stalls = StallStats(startupTimeMs = 720))

        advanceUntilIdle()

        assertEquals(720L, viewModel.state.value.metrics.stalls.startupTimeMs)
    }

    @Test
    fun `open manifest requests navigation for the current stream`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.OpenManifest)

            assertEquals(PlayerEffect.NavigateToManifest(SampleStreams.bigBuckBunny.id), awaitItem())
        }
    }

    @Test
    fun `stats overlay is visible by default and toggles`() {
        assertTrue(viewModel.state.value.isStatsVisible)

        viewModel.onIntent(PlayerIntent.ToggleStats)
        assertFalse(viewModel.state.value.isStatsVisible)

        viewModel.onIntent(PlayerIntent.ToggleStats)
        assertTrue(viewModel.state.value.isStatsVisible)
    }

    @Test
    fun `stats come straight from the engine`() {
        engine.stats.value = StatsState(playerState = "Ready", droppedFrames = 4)

        assertEquals(StatsState(playerState = "Ready", droppedFrames = 4), viewModel.stats.value)
    }

    @Test
    fun `seek by forwards the offset to the engine`() {
        viewModel.onIntent(PlayerIntent.SeekBy(60_000))
        viewModel.onIntent(PlayerIntent.SeekBy(-10_000))

        assertEquals(listOf(60_000L, -10_000L), engine.seekOffsets)
    }

    @Test
    fun `network is unlimited by default`() {
        assertEquals(ThrottleMode.UNLIMITED, viewModel.state.value.throttleMode)
    }

    @Test
    fun `changing the network mode applies it at runtime without restarting the player`() {
        viewModel.onIntent(PlayerIntent.SetThrottleMode(ThrottleMode.KBPS_800))

        assertEquals(ThrottleMode.KBPS_800, viewModel.state.value.throttleMode)
        assertEquals(listOf(ThrottleMode.KBPS_800), engine.throttleModes)
        assertEquals("no rebuild", 1, engine.startedConfigs.size)
    }

    // --- Buffer settings (DataStore) ---

    @Test
    fun `loading saved settings does not restart a player that uses Media3 defaults`() = runTest {
        // Already in DataStore before the player screen opens.
        settings = FakeBufferSettingsRepository(BufferPreset.STABLE_VOD.settings)
        val vm = create(mapOf(PlayerViewModel.KEY_STREAM_ID to SampleStreams.bigBuckBunny.id))

        advanceUntilIdle()

        assertEquals("no rebuild", 1, engine.startedConfigs.size)
        assertEquals(BufferPreset.STABLE_VOD.settings, vm.state.value.config.buffer)
        assertEquals(false, vm.state.value.config.tunedBuffer)
    }

    @Test
    fun `changed settings switch tuning on and rebuild the player`() = runTest {
        advanceUntilIdle() // initial load

        settings.settings.value = BufferPreset.LOW_MEMORY_TV.settings
        advanceUntilIdle()

        val rebuilt = engine.startedConfigs.last()
        assertEquals(2, engine.startedConfigs.size)
        assertTrue(rebuilt.tunedBuffer)
        assertEquals(BufferPreset.LOW_MEMORY_TV.settings, rebuilt.buffer)
        assertNull("same stream: the engine keeps the position", engine.startPositions.last())
    }

    @Test
    fun `settings changed while the player is hidden are applied when it comes back`() = runTest {
        advanceUntilIdle()
        viewModel.onIntent(PlayerIntent.LifecycleStop) // settings screen opened

        settings.settings.value = BufferPreset.STABLE_VOD.settings
        advanceUntilIdle()
        assertEquals("no rebuild while hidden (it would start playing)", 1, engine.startedConfigs.size)

        viewModel.onIntent(PlayerIntent.LifecycleStart)

        assertEquals(2, engine.startedConfigs.size)
        assertEquals(BufferPreset.STABLE_VOD.settings, engine.startedConfigs.last().buffer)
        assertEquals("rebuild replaces resume", 0, engine.resumeCount)
    }

    @Test
    fun `open buffer settings requests navigation`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(PlayerIntent.OpenBufferSettings)

            assertEquals(PlayerEffect.NavigateToBufferSettings, awaitItem())
        }
    }

    @Test
    fun `toggle guide hides and shows the overlay`() {
        viewModel.onIntent(PlayerIntent.ToggleGuide)
        assertFalse(viewModel.state.value.isGuideVisible)

        viewModel.onIntent(PlayerIntent.ToggleGuide)
        assertTrue(viewModel.state.value.isGuideVisible)
    }
}
