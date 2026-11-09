package com.example.livex.manifest

import java.net.URI

/** What a highlighted range means; the UI maps each kind to a color + legend entry. */
enum class HighlightKind(val legend: String) {
    STRUCTURE("Structure"),
    ATTRIBUTE("Quality attributes"),
    LIVE("Live window / timing"),
}

/** [start] inclusive, [end] exclusive character offsets into the manifest text. */
data class Highlight(val start: Int, val end: Int, val kind: HighlightKind)

/** One quality level listed in an HLS master playlist. */
data class HlsVariant(val label: String, val url: String)

sealed interface ManifestSummary {
    /** HLS master (multivariant) playlist: only lists variants, no segments. */
    data class HlsMaster(val variantCount: Int) : ManifestSummary

    /** HLS media playlist: the actual segment list for one variant. */
    data class HlsMedia(
        val targetDurationSec: Int?,
        val mediaSequence: Long?,
        val segmentCount: Int,
        val hasEndList: Boolean,
    ) : ManifestSummary

    /** DASH MPD: one document describing everything (Period → AdaptationSet → Representation). */
    data class Dash(
        val isDynamic: Boolean,
        val periods: Int,
        val adaptationSets: Int,
        val representations: Int,
        val minimumUpdatePeriod: String?,
        val timeShiftBufferDepth: String?,
    ) : ManifestSummary

    data object Unknown : ManifestSummary
}

fun ManifestSummary.describe(): String = when (this) {
    is ManifestSummary.HlsMaster ->
        "HLS master playlist · $variantCount variants · pick a variant to open its media playlist"
    is ManifestSummary.HlsMedia -> buildString {
        append("HLS media playlist")
        targetDurationSec?.let { append(" · target duration ${it}s") }
        mediaSequence?.let { append(" · media sequence $it") }
        append(" · $segmentCount segments · ")
        append(if (hasEndList) "VOD (has EXT-X-ENDLIST)" else "live (no EXT-X-ENDLIST, refresh to see it slide)")
    }
    is ManifestSummary.Dash -> buildString {
        append("DASH MPD · type=${if (isDynamic) "dynamic (live)" else "static (VOD)"}")
        append(" · $periods Period · $adaptationSets AdaptationSet · $representations Representation")
        minimumUpdatePeriod?.let { append(" · refresh every $it") }
        timeShiftBufferDepth?.let { append(" · DVR window $it") }
    }
    ManifestSummary.Unknown -> "Unrecognized manifest format"
}

/** Pure text analysis of HLS playlists and DASH MPDs (no Android, no network), unit-tested. */
object ManifestAnalyzer {

    private val highlightRules: List<Pair<Regex, HighlightKind>> = listOf(
        // HLS
        Regex("""#EXT-X-STREAM-INF|#EXTINF:[\d.]+|#EXT-X-TARGETDURATION:\d+""") to HighlightKind.STRUCTURE,
        Regex("""#EXT-X-MEDIA-SEQUENCE:\d+""") to HighlightKind.LIVE,
        // Lookbehind keeps AVERAGE-BANDWIDTH from matching BANDWIDTH.
        Regex("""(?<![A-Z-])(BANDWIDTH|RESOLUTION)=[^,\s]+""") to HighlightKind.ATTRIBUTE,
        // DASH
        Regex("""</?(Period|AdaptationSet|Representation)\b""") to HighlightKind.STRUCTURE,
        Regex("""(?<![\w-])bandwidth="[^"]*"""") to HighlightKind.ATTRIBUTE,
        Regex("""(?<![\w-])(type="dynamic"|(availabilityStartTime|timeShiftBufferDepth|minimumUpdatePeriod)="[^"]*")""") to
            HighlightKind.LIVE,
    )

    private val streamInfAttribute = Regex("""(?<![A-Z-])(BANDWIDTH|RESOLUTION)=([^,\s]+)""")

    fun highlights(text: String): List<Highlight> {
        val all = highlightRules
            .flatMap { (regex, kind) -> regex.findAll(text).map { Highlight(it.range.first, it.range.last + 1, kind) } }
            .sortedBy { it.start }
        // Drop anything overlapping an earlier range so the UI never applies two styles to one char.
        val result = mutableListOf<Highlight>()
        for (h in all) if (result.isEmpty() || h.start >= result.last().end) result += h
        return result
    }

    fun summarize(text: String): ManifestSummary {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("#EXTM3U") -> summarizeHls(text)
            trimmed.contains("<MPD") -> summarizeDash(text)
            else -> ManifestSummary.Unknown
        }
    }

    /** Variant URIs from a master playlist, resolved against [baseUrl] (they are often relative). */
    fun hlsVariants(text: String, baseUrl: String): List<HlsVariant> {
        val lines = text.lines().map { it.trim() }
        val base = URI(baseUrl)
        return lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
            // The URI is the next non-empty, non-tag line.
            val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@mapIndexedNotNull null
            val attributes = streamInfAttribute.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
            val label = listOfNotNull(
                attributes["RESOLUTION"] ?: "audio only",
                attributes["BANDWIDTH"]?.toLongOrNull()?.let { "${it / 1000} kbps" },
            ).joinToString(" · ")
            HlsVariant(label = label, url = base.resolve(uri).toString())
        }
    }

    private fun summarizeHls(text: String): ManifestSummary {
        val lines = text.lines().map { it.trim() }
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF:") }) {
            return ManifestSummary.HlsMaster(variantCount = lines.count { it.startsWith("#EXT-X-STREAM-INF:") })
        }
        fun value(tag: String) = lines.firstOrNull { it.startsWith("$tag:") }?.substringAfter(':')?.trim()
        return ManifestSummary.HlsMedia(
            targetDurationSec = value("#EXT-X-TARGETDURATION")?.toIntOrNull(),
            mediaSequence = value("#EXT-X-MEDIA-SEQUENCE")?.toLongOrNull(),
            segmentCount = lines.count { it.startsWith("#EXTINF:") },
            hasEndList = lines.any { it == "#EXT-X-ENDLIST" },
        )
    }

    private fun summarizeDash(text: String): ManifestSummary {
        fun count(element: String) = Regex("""<$element\b""").findAll(text).count()
        fun mpdAttribute(name: String) =
            Regex("""(?<![\w-])$name="([^"]*)"""").find(text)?.groupValues?.get(1)
        return ManifestSummary.Dash(
            isDynamic = Regex("""(?<![\w-])type="dynamic"""").containsMatchIn(text),
            periods = count("Period"),
            adaptationSets = count("AdaptationSet"),
            representations = count("Representation"),
            minimumUpdatePeriod = mpdAttribute("minimumUpdatePeriod"),
            timeShiftBufferDepth = mpdAttribute("timeShiftBufferDepth"),
        )
    }
}
