package com.example.livex.player.ads

/** Recent IMA ad events for the stats overlay (oldest first). Pure Kotlin. */
class AdEventLog(private val maxEntries: Int = 5) {

    data class Entry(val text: String, val isError: Boolean)

    private val recent = ArrayDeque<Entry>()

    val entries: List<Entry> get() = recent.toList()

    fun add(name: String, detail: String?, isError: Boolean = false): Entry {
        val entry = Entry(text = if (detail.isNullOrBlank()) name else "$name · $detail", isError = isError)
        recent.addLast(entry)
        while (recent.size > maxEntries) recent.removeFirst()
        return entry
    }

    fun clear() = recent.clear()
}
