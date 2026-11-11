package com.example.livex.qoe

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.qoe.store.FakeQoeSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Robolectric only for android.util.Log in the failure path. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class QoePipelineTest {

    private class RecordingReporter(override val name: String, val fail: Boolean = false) : QoeReporter {
        val received = mutableListOf<QoeEvent>()
        override suspend fun report(event: QoeEvent) {
            if (fail) throw IOException("backend down")
            received += event
        }
    }

    private val start = QoeEvent.SessionStart("s1", 0, "bbb", "Big Buck Bunny", false, 0)
    private val vst = QoeEvent.VideoStart("s1", 800, 800)

    @Test
    fun `every event is stored and sent to every reporter, in order`() = runTest {
        val bus = QoeEventBus()
        val repository = FakeQoeSessionRepository()
        val a = RecordingReporter("a")
        val b = RecordingReporter("b")
        val pipeline = QoePipeline(bus, repository, setOf(a, b), UnconfinedTestDispatcher(testScheduler))
        val job = pipeline.start()

        bus.emit(start)
        bus.emit(vst)

        assertEquals(listOf(start, vst), repository.recorded)
        assertEquals(listOf(start, vst), a.received)
        assertEquals(listOf(start, vst), b.received)
        job.cancel()
    }

    @Test
    fun `a failing sink doesn't stop the others or later events`() = runTest {
        val bus = QoeEventBus()
        val repository = FakeQoeSessionRepository().apply { failWith = IllegalStateException("disk full") }
        val broken = RecordingReporter("broken", fail = true)
        val healthy = RecordingReporter("healthy")
        val pipeline = QoePipeline(bus, repository, setOf(broken, healthy), UnconfinedTestDispatcher(testScheduler))
        val job = pipeline.start()

        bus.emit(start)
        bus.emit(vst)

        assertEquals(listOf(start, vst), healthy.received)
        job.cancel()
    }
}
