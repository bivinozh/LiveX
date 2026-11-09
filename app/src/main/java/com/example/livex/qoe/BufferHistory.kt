package com.example.livex.qoe

/** One sample for the buffer chart. */
data class BufferPoint(
    /** Elapsed-realtime timestamp of the sample. */
    val atMs: Long,
    val bufferedMs: Long,
    /** True if the player was stalled at that moment (drawn in red). */
    val isStalled: Boolean,
)

/** Rolling window of buffer samples (the last [windowMs]), oldest first. */
class BufferHistory(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private val samples = ArrayDeque<BufferPoint>()

    val points: List<BufferPoint> get() = samples.toList()

    fun add(point: BufferPoint) {
        samples.addLast(point)
        while (samples.isNotEmpty() && point.atMs - samples.first().atMs > windowMs) samples.removeFirst()
    }

    fun clear() = samples.clear()

    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
    }
}
