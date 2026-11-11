package com.example.livex.qoe

import android.util.Log
import com.example.livex.di.IoDispatcher
import com.example.livex.qoe.store.QoeSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The consumer side: one coroutine (started by the Application) takes every event off the bus,
 * stores it (Sessions screen) and hands it to each [QoeReporter].
 *
 * Events are processed one at a time, in order, off the main thread. A failing sink is logged and
 * skipped: analytics must never take down playback, nor one backend the others.
 */
@Singleton
class QoePipeline @Inject constructor(
    private val bus: QoeEventBus,
    private val repository: QoeSessionRepository,
    private val reporters: Set<@JvmSuppressWildcards QoeReporter>,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null

    fun start(): Job = job ?: scope.launch {
        bus.events.collect { event ->
            safely("store") { repository.record(event) }
            reporters.forEach { reporter -> safely(reporter.name) { reporter.report(event) } }
        }
    }.also { job = it }

    private suspend fun safely(sink: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "QoE sink '$sink' failed", e)
        }
    }

    private companion object {
        const val TAG = "LiveX-QoE"
    }
}
