package com.example.livex.qoe.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.livex.qoe.EndReason
import com.example.livex.qoe.QoeEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room (in-memory SQLite) on Robolectric. */
@RunWith(AndroidJUnit4::class)
class RoomQoeSessionRepositoryTest {

    private lateinit var database: LiveXDatabase
    private lateinit var repository: RoomQoeSessionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiveXDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomQoeSessionRepository(database.qoeDao())
    }

    @After
    fun tearDown() = database.close()

    private fun start(id: String, epochMs: Long) =
        QoeEvent.SessionStart(id, 1_000, "bbb", "Big Buck Bunny", false, epochMs)

    @Test
    fun `a session's events are stored and summarised`() = runBlocking {
        repository.record(start("s1", 1_000_000))
        repository.record(QoeEvent.VideoStart("s1", 1_800, 800))
        repository.record(QoeEvent.StallStart("s1", 5_000))
        repository.record(QoeEvent.StallEnd("s1", 5_600, 600))
        repository.record(QoeEvent.SessionEnd("s1", 11_000, EndReason.EXIT, 9_400, 600, 1_500_000))

        val summary = repository.sessions.first().single()
        assertEquals(800L, summary.vstMs)
        assertEquals(1, summary.stallCount)
        assertEquals(0.06, summary.rebufferingRatio!!, 1e-9)
        assertEquals(1_500_000, summary.avgBitrateBps)
        assertEquals(EndReason.EXIT, summary.endReason)
        assertEquals(10_000L, summary.durationMs)

        val events = database.qoeDao().events("s1")
        assertEquals(
            listOf("session_start", "video_start", "stall_start", "stall_end", "session_end"),
            events.map { it.type },
        )
        assertEquals(listOf(0L, 800L, 4_000L, 4_600L, 10_000L), events.map { it.offsetMs })
    }

    @Test
    fun `an open session is visible before it ends`() = runBlocking {
        repository.record(start("s1", 1_000_000))
        repository.record(QoeEvent.VideoStart("s1", 1_900, 900))

        val summary = repository.sessions.first().single()
        assertEquals(900L, summary.vstMs)
        assertNull(summary.endReason)
    }

    @Test
    fun `newest session first`() = runBlocking {
        repository.record(start("old", 1_000_000))
        repository.record(start("new", 2_000_000))

        assertEquals(listOf("new", "old"), repository.sessions.first().map { it.sessionId })
    }

    @Test
    fun `events without a known session are ignored`() = runBlocking {
        repository.record(QoeEvent.VideoStart("lost", 1_800, 800))

        assertTrue(repository.sessions.first().isEmpty())
    }

    @Test
    fun `clear removes sessions and their events`() = runBlocking {
        repository.record(start("s1", 1_000_000))
        repository.record(QoeEvent.VideoStart("s1", 1_800, 800))

        repository.clear()

        assertTrue(repository.sessions.first().isEmpty())
        assertTrue(database.qoeDao().events("s1").isEmpty())
    }
}
