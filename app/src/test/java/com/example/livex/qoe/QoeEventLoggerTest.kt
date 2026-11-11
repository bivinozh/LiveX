package com.example.livex.qoe

import com.example.livex.data.SampleStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QoeEventLoggerTest {

    private val events = mutableListOf<QoeEvent>()
    private var nextId = 0
    private lateinit var logger: QoeEventLogger

    private val stream = SampleStreams.bigBuckBunny

    @Before
    fun setUp() {
        logger = QoeEventLogger(emit = { events += it }, newSessionId = { "s${++nextId}" })
    }

    private inline fun <reified T : QoeEvent> only(): List<T> = events.filterIsInstance<T>()

    private fun metrics(
        startupMs: Long? = null,
        playingMs: Long = 0,
        stallMs: Long = 0,
        stallCount: Int = 0,
        stalled: Boolean = false,
        bitrate: Int? = null,
        height: Int = 0,
        switches: Int = 0,
        loadErrors: Int = 0,
    ) = QoeMetrics(
        stalls = StallStats(
            startupTimeMs = startupMs,
            stallCount = stallCount,
            stallTimeMs = stallMs,
            playingTimeMs = playingMs,
            isStalled = stalled,
        ),
        videoWidth = height * 16 / 9,
        videoHeight = height,
        videoBitrateBps = bitrate,
        qualitySwitches = switches,
        loadErrorCount = loadErrors,
    )

    @Test
    fun `session start describes the stream`() {
        logger.startSession(stream, nowMs = 1_000, epochMs = 1_700_000_000_000)

        val start = only<QoeEvent.SessionStart>().single()
        assertEquals("s1", start.sessionId)
        assertEquals(stream.id, start.streamId)
        assertEquals(stream.title, start.title)
        assertEquals(1_700_000_000_000, start.startedAtEpochMs)
        assertEquals("s1", logger.sessionId)
    }

    @Test
    fun `video start is emitted once with the VST`() {
        logger.startSession(stream, 1_000, 0)

        logger.onMetrics(metrics(startupMs = 850), nowMs = 1_850)
        logger.onMetrics(metrics(startupMs = 850, playingMs = 500), nowMs = 2_350)

        assertEquals(listOf(850L), only<QoeEvent.VideoStart>().map { it.vstMs })
    }

    @Test
    fun `stall start and end carry the stall duration from the tracker`() {
        logger.startSession(stream, 1_000, 0)
        logger.onMetrics(metrics(startupMs = 800, playingMs = 4_000), nowMs = 5_000)

        logger.onMetrics(metrics(startupMs = 800, playingMs = 4_000, stallCount = 1, stalled = true), nowMs = 5_000)
        logger.onMetrics(metrics(startupMs = 800, playingMs = 4_000, stallCount = 1, stallMs = 1_200), nowMs = 6_200)

        assertEquals(1, only<QoeEvent.StallStart>().size)
        assertEquals(listOf(1_200L), only<QoeEvent.StallEnd>().map { it.durationMs })
    }

    @Test
    fun `bitrate switch reports from and to renditions`() {
        logger.startSession(stream, 1_000, 0)
        logger.onMetrics(metrics(bitrate = 800_000, height = 360), nowMs = 1_500)

        logger.onMetrics(metrics(bitrate = 2_500_000, height = 720, switches = 1), nowMs = 9_000)

        val switch = only<QoeEvent.BitrateSwitch>().single()
        assertEquals(800_000, switch.fromBps)
        assertEquals(2_500_000, switch.toBps)
        assertEquals(720, switch.height)
    }

    @Test
    fun `failed requests are non-fatal errors, player errors are fatal`() {
        logger.startSession(stream, 1_000, 0)

        logger.onMetrics(metrics(loadErrors = 1), nowMs = 2_000)
        logger.onPlayerError("ERROR_CODE_IO_BAD_HTTP_STATUS", "Server error", nowMs = 3_000)

        assertEquals(listOf(false, true), only<QoeEvent.PlaybackError>().map { it.isFatal })
    }

    @Test
    fun `ad events are recorded`() {
        logger.startSession(stream, 1_000, 0)

        logger.onAdEvent("STARTED", "pre-roll · ad 1/1 · 10 s", isError = false, nowMs = 1_200)

        val ad = only<QoeEvent.Ad>().single()
        assertEquals("STARTED · pre-roll · ad 1/1 · 10 s", ad.detail)
    }

    @Test
    fun `leaving before the first frame is EBVS`() {
        logger.startSession(stream, 1_000, 0)

        logger.endSession(nowMs = 5_000, reason = EndReason.EXIT)

        assertEquals(listOf(4_000L), only<QoeEvent.Ebvs>().map { it.waitedMs })
        assertTrue(events.last() is QoeEvent.SessionEnd)
        assertNull(logger.sessionId)
    }

    @Test
    fun `no EBVS after the video started or when startup failed`() {
        logger.startSession(stream, 1_000, 0)
        logger.onMetrics(metrics(startupMs = 900), nowMs = 1_900)
        logger.endSession(nowMs = 9_000, reason = EndReason.EXIT)

        logger.startSession(stream, 10_000, 0)
        logger.onPlayerError("ERROR_CODE_DECODING_FAILED", "Can't decode", nowMs = 10_500)
        logger.endSession(nowMs = 12_000, reason = EndReason.EXIT)

        assertTrue(only<QoeEvent.Ebvs>().isEmpty())
    }

    @Test
    fun `a player rebuild ends the old session and starts a new one`() {
        logger.startSession(stream, 1_000, 0)

        logger.startSession(stream, 8_000, 0)

        val end = only<QoeEvent.SessionEnd>().single()
        assertEquals("s1", end.sessionId)
        assertEquals(EndReason.RESTARTED, end.reason)
        assertTrue(only<QoeEvent.Ebvs>().isEmpty()) // not the viewer leaving
        assertEquals(listOf("s1", "s2"), only<QoeEvent.SessionStart>().map { it.sessionId })
    }

    @Test
    fun `session end has playing time, stall time and the time-weighted bitrate`() {
        logger.startSession(stream, 0, 0)
        logger.onMetrics(metrics(startupMs = 500, bitrate = 1_000_000), nowMs = 500)
        // 3 s at 1 Mbps, then a switch to 4 Mbps and 1 s at 4 Mbps -> (3*1 + 1*4) / 4 = 1.75 Mbps
        logger.onMetrics(metrics(startupMs = 500, playingMs = 3_000, bitrate = 1_000_000), nowMs = 3_500)
        logger.onMetrics(metrics(startupMs = 500, playingMs = 3_000, bitrate = 4_000_000, switches = 1), nowMs = 3_500)
        logger.onMetrics(
            metrics(startupMs = 500, playingMs = 4_000, stallMs = 300, stallCount = 1, bitrate = 4_000_000, switches = 1),
            nowMs = 4_800,
        )

        logger.endSession(nowMs = 5_000, reason = EndReason.COMPLETED)

        val end = only<QoeEvent.SessionEnd>().single()
        assertEquals(4_000L, end.playingTimeMs)
        assertEquals(300L, end.stallTimeMs)
        assertEquals(1_750_000, end.avgBitrateBps)
    }

    @Test
    fun `an open stall is closed when the session ends`() {
        logger.startSession(stream, 0, 0)
        logger.onMetrics(metrics(startupMs = 500, playingMs = 1_000), nowMs = 1_500)
        logger.onMetrics(metrics(startupMs = 500, playingMs = 1_000, stallCount = 1, stalled = true), nowMs = 1_500)
        logger.onMetrics(metrics(startupMs = 500, playingMs = 1_000, stallCount = 1, stallMs = 2_000, stalled = true), nowMs = 3_500)

        logger.endSession(nowMs = 3_500, reason = EndReason.EXIT)

        assertEquals(listOf(2_000L), only<QoeEvent.StallEnd>().map { it.durationMs })
    }

    @Test
    fun `nothing is emitted outside a session`() {
        logger.onMetrics(metrics(startupMs = 500), nowMs = 500)
        logger.onPlayerError("X", "y", nowMs = 600)
        logger.endSession(nowMs = 700, reason = EndReason.EXIT)

        assertTrue(events.isEmpty())
    }
}
