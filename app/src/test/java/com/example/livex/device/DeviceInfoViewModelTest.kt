package com.example.livex.device

import com.example.livex.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInfoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val info = DeviceInfo(
        model = "Test TV",
        sdkInt = 36,
        isTelevision = true,
        codecs = emptyList(),
        display = DisplayInfo(1280, 720, 60f, 1280, 720, emptySet()),
        memory = MemoryInfo(2048, 192, false),
        widevine = null,
    )

    private class FakeReader(var result: () -> DeviceInfo) : DeviceInfoReader {
        var reads = 0
        override fun read(): DeviceInfo {
            reads++
            return result()
        }
    }

    @Test
    fun `loads device info on start`() = runTest {
        val vm = DeviceInfoViewModel(FakeReader { info }, StandardTestDispatcher(testScheduler))
        assertTrue(vm.state.value.isLoading)

        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(info, vm.state.value.info)
    }

    @Test
    fun `a failing read shows an error`() = runTest {
        val vm = DeviceInfoViewModel(FakeReader { error("MediaCodecList unavailable") }, StandardTestDispatcher(testScheduler))

        advanceUntilIdle()

        assertEquals("MediaCodecList unavailable", vm.state.value.error)
        assertNull(vm.state.value.info)
    }

    @Test
    fun `refresh reads again`() = runTest {
        val reader = FakeReader { info }
        val vm = DeviceInfoViewModel(reader, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        vm.onIntent(DeviceInfoIntent.Refresh)
        advanceUntilIdle()

        assertEquals(2, reader.reads)
    }
}
