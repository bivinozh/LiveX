package com.example.livex.qoe

import android.util.Log
import javax.inject.Inject

/**
 * Where QoE events leave the device. One implementation per analytics backend; all of them are
 * bound into a Set (Hilt @IntoSet in QoeModule) and QoePipeline sends every event to each.
 *
 * Plugging in a real backend means adding an implementation, never touching the player:
 * - Conviva: a ConvivaQoeReporter maps session_start to "playback requested" on a Conviva video
 *   analytics session (with content metadata: asset name, live/VOD, stream URL), the player states
 *   and bitrate switches to Conviva's playback metrics, errors to fatal/warning errors, and
 *   session_end to releasing the session. Conviva computes VST, EBVS and rebuffering ratio itself.
 * - Mux Data: its Media3 SDK usually attaches to the ExoPlayer directly and gathers most of this
 *   automatically; a reporter would only add custom dimensions (experiment, CDN, device class).
 * - Custom (e.g. Hydrolix, ClickHouse, BigQuery): batch the events as JSON lines and POST them to
 *   your ingest endpoint; the dashboards are SQL over the raw events. You own the definitions.
 *
 * Keys/tokens come from build config or remote config, never from source control.
 */
interface QoeReporter {
    val name: String

    /** Called sequentially, off the main thread. May suspend (network); must not throw for bad data. */
    suspend fun report(event: QoeEvent)
}

/**
 * Stand-in backend: builds the payload a real ingest endpoint would get, batches it like an SDK
 * would (fewer, larger requests save battery and radio wake-ups) and "sends" each batch to logcat
 * (tag LiveX-QoE). Session ends flush immediately: the app may be killed right after.
 */
class FakeQoeReporter(
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val send: (List<Map<String, Any?>>) -> Unit = ::logBatch,
) : QoeReporter {

    @Inject constructor() : this(DEFAULT_BATCH_SIZE)

    override val name = "fake"

    private val pending = mutableListOf<Map<String, Any?>>()

    override suspend fun report(event: QoeEvent) {
        pending += payload(event)
        if (pending.size >= batchSize || event is QoeEvent.SessionEnd) flush()
    }

    private fun flush() {
        if (pending.isEmpty()) return
        send(pending.toList())
        pending.clear()
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        private const val TAG = "LiveX-QoE"

        /** The wire format: flat key/values, like a JSON object per event. */
        fun payload(event: QoeEvent): Map<String, Any?> = buildMap {
            put("session_id", event.sessionId)
            put("type", event.type)
            put("t_ms", event.atMs)
            when (event) {
                is QoeEvent.SessionStart -> {
                    put("stream_id", event.streamId)
                    put("title", event.title)
                    put("live", event.isLive)
                    put("started_at", event.startedAtEpochMs)
                }
                is QoeEvent.VideoStart -> put("vst_ms", event.vstMs)
                is QoeEvent.StallStart -> Unit
                is QoeEvent.StallEnd -> put("duration_ms", event.durationMs)
                is QoeEvent.BitrateSwitch -> {
                    put("from_bps", event.fromBps)
                    put("to_bps", event.toBps)
                    put("height", event.height)
                }
                is QoeEvent.Ad -> {
                    put("ad_event", event.name)
                    put("info", event.info)
                }
                is QoeEvent.PlaybackError -> {
                    put("code", event.code)
                    put("message", event.message)
                    put("fatal", event.isFatal)
                }
                is QoeEvent.Ebvs -> put("waited_ms", event.waitedMs)
                is QoeEvent.SessionEnd -> {
                    put("reason", event.reason.name)
                    put("playing_ms", event.playingTimeMs)
                    put("stall_ms", event.stallTimeMs)
                    put("avg_bitrate_bps", event.avgBitrateBps)
                }
            }
        }

        private fun logBatch(batch: List<Map<String, Any?>>) {
            Log.i(TAG, "POST /ingest (fake): ${batch.size} event(s)")
            batch.forEach { Log.d(TAG, "  $it") }
        }
    }
}
