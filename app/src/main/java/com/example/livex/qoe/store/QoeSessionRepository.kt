package com.example.livex.qoe.store

import com.example.livex.qoe.EndReason
import com.example.livex.qoe.QoeEvent
import com.example.livex.qoe.SessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface QoeSessionRepository {
    /** Newest first. */
    val sessions: Flow<List<SessionSummary>>

    suspend fun record(event: QoeEvent)

    suspend fun clear()
}

@Singleton
class RoomQoeSessionRepository @Inject constructor(
    private val dao: QoeDao,
) : QoeSessionRepository {

    /** Summaries of open sessions, so recording an event doesn't need a read first. */
    private val open = mutableMapOf<String, SessionSummary>()
    private val mutex = Mutex()

    override val sessions: Flow<List<SessionSummary>> =
        dao.sessions().map { rows -> rows.map { it.toSummary() } }

    override suspend fun record(event: QoeEvent) = mutex.withLock {
        val summary = when (event) {
            is QoeEvent.SessionStart -> SessionSummary.start(event)
            // Session start lost (bus overflow) or already cleared: nothing to attach the event to.
            else -> (open[event.sessionId] ?: dao.session(event.sessionId)?.toSummary() ?: return@withLock)
                .applyEvent(event)
        }
        if (event is QoeEvent.SessionEnd) open.remove(event.sessionId) else open[event.sessionId] = summary
        dao.record(
            summary.toEntity(),
            QoeEventEntity(
                sessionId = event.sessionId,
                offsetMs = event.atMs - summary.startAtMs,
                type = event.type,
                detail = event.detail,
            ),
        )
    }

    override suspend fun clear() = mutex.withLock {
        open.clear()
        dao.clear()
    }
}

internal fun QoeSessionEntity.toSummary() = SessionSummary(
    sessionId = sessionId,
    streamId = streamId,
    title = title,
    isLive = isLive,
    startedAtEpochMs = startedAtEpochMs,
    startAtMs = startAtMs,
    durationMs = durationMs,
    vstMs = vstMs,
    isEbvs = isEbvs,
    ebvsWaitMs = ebvsWaitMs,
    stallCount = stallCount,
    stallTimeMs = stallTimeMs,
    playingTimeMs = playingTimeMs,
    avgBitrateBps = avgBitrateBps,
    bitrateSwitches = bitrateSwitches,
    failures = failures,
    lastError = lastError,
    endReason = endReason?.let { name -> EndReason.entries.firstOrNull { it.name == name } },
)

internal fun SessionSummary.toEntity() = QoeSessionEntity(
    sessionId = sessionId,
    streamId = streamId,
    title = title,
    isLive = isLive,
    startedAtEpochMs = startedAtEpochMs,
    startAtMs = startAtMs,
    durationMs = durationMs,
    vstMs = vstMs,
    isEbvs = isEbvs,
    ebvsWaitMs = ebvsWaitMs,
    stallCount = stallCount,
    stallTimeMs = stallTimeMs,
    playingTimeMs = playingTimeMs,
    avgBitrateBps = avgBitrateBps,
    bitrateSwitches = bitrateSwitches,
    failures = failures,
    lastError = lastError,
    endReason = endReason?.name,
)
