package com.localplayer.music.data.lyrics

/** One synchronized lyric line. [timestampMs] is the playback position at which the line becomes active. */
data class LyricLine(val timestampMs: Long, val text: String)

/**
 * Lyrics as read from the file.
 * [isSynced] = at least one valid timestamp was found. [plainText] is always the readable text
 * (timestamps and tags removed), so any lyrics can be shown even without synchronization.
 */
data class ParsedLyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val plainText: String,
    val tags: Map<String, String> = emptyMap(),
    val offsetMs: Long = 0L,
) {
    val isEmpty: Boolean get() = plainText.isBlank() && lines.isEmpty()
}

/** Never throws: malformed input degrades to plain text. */
object LrcParser {
    // [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx] (also accepts ':' before the fraction)
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[([A-Za-z#]+):([^\]]*)]\s*$""")
    private val enhancedWord = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(raw: String?): ParsedLyrics {
        if (raw.isNullOrBlank()) return ParsedLyrics(emptyList(), false, "")
        val tags = LinkedHashMap<String, String>()
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<String>()

        for (rawLine in raw.split('\n')) {
            val line = rawLine.trimEnd('\r')
            val meta = metaTag.matchEntire(line.trim())
            if (meta != null && timestamp.find(line) == null) {
                tags[meta.groupValues[1].lowercase()] = meta.groupValues[2].trim()
                continue
            }
            val stamps = leadingTimestamps(line)
            if (stamps.isEmpty()) {
                plain.add(line)
                continue
            }
            val text = line.substring(stamps.last().endExclusive).replace(enhancedWord, "").trim()
            for (stamp in stamps) synced.add(LyricLine(stamp.ms, text))
            plain.add(text)
        }

        val offset = tags["offset"]?.trim()?.removePrefix("+")?.toLongOrNull() ?: 0L
        // Per the LRC convention a positive offset makes lyrics appear EARLIER.
        val lines = synced
            .map { LyricLine((it.timestampMs - offset).coerceAtLeast(0L), it.text) }
            .sortedBy { it.timestampMs } // stable sort: same-time lines keep their order
        val readable = plain.joinToString("\n").trim('\n')
        return ParsedLyrics(
            lines = lines,
            isSynced = lines.isNotEmpty(),
            plainText = readable,
            tags = tags,
            offsetMs = offset,
        )
    }

    private class Stamp(val ms: Long, val endExclusive: Int)

    /** All timestamps at the start of the line, e.g. "[00:15.00][01:20.00]text". */
    private fun leadingTimestamps(line: String): List<Stamp> {
        val result = ArrayList<Stamp>()
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index] == ' ') index++
            val match = timestamp.matchAt(line, index) ?: break
            result.add(Stamp(toMillis(match), match.range.last + 1))
            index = match.range.last + 1
        }
        return result
    }

    internal fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + fractionMs
    }

    /** Parses "mm:ss", "mm:ss.xx" or "mm:ss.xxx" (no brackets). Null when it is not a timestamp. */
    fun parseTimestamp(text: String): Long? {
        val match = timestamp.matchEntire("[${text.trim()}]") ?: return null
        val seconds = match.groupValues[2].toLong()
        if (seconds >= 60) return null
        return toMillis(match)
    }

    /** "mm:ss.xx" (centiseconds), the usual LRC form. */
    fun formatTimestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val minutes = total / 60_000
        val seconds = (total % 60_000) / 1_000
        val centis = (total % 1_000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, centis)
    }
}

/** Which synchronized line is active at a playback position. */
object LyricsSync {
    /** Index of the last line whose timestamp is <= [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    /** Milliseconds until the next line becomes active, or null after the last line. */
    fun msUntilNextLine(lines: List<LyricLine>, positionMs: Long): Long? {
        val next = activeIndex(lines, positionMs) + 1
        return if (next in lines.indices) (lines[next].timestampMs - positionMs).coerceAtLeast(0L) else null
    }
}
