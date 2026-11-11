package com.example.livex.qoe.store

import com.example.livex.qoe.QoeEvent
import com.example.livex.qoe.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeQoeSessionRepository(initial: List<SessionSummary> = emptyList()) : QoeSessionRepository {
    override val sessions: StateFlow<List<SessionSummary>> = MutableStateFlow(initial)
    val recorded = mutableListOf<QoeEvent>()
    var failWith: Exception? = null
    var clearCount = 0

    override suspend fun record(event: QoeEvent) {
        failWith?.let { throw it }
        recorded += event
    }

    override suspend fun clear() {
        clearCount++
        (sessions as MutableStateFlow).value = emptyList()
    }
}
