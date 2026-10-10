package com.localplayer.music.data.lyrics

/** One editable row of the lyrics editor. [timeText] is empty for a plain (unsynchronized) line. */
data class EditorLine(val id: Long, val timeText: String, val text: String)

/** The editor's document: lines plus any LRC header tags ([ti:], [ar:], [offset:] ...) kept as they were. */
data class EditorDocument(val header: List<String>, val lines: List<EditorLine>)

object LyricsEditorModel {
    private val timestampStart = Regex("""^\s*\[(\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val metaTag = Regex("""^\s*\[[A-Za-z#]+:[^\]]*]\s*$""")

    /** Splits stored lyrics into editable rows. A line with several timestamps becomes several rows. */
    fun fromRaw(raw: String?, startId: Long = 1L): EditorDocument {
        if (raw.isNullOrEmpty()) return EditorDocument(emptyList(), emptyList())
        var id = startId
        val header = ArrayList<String>()
        val lines = ArrayList<EditorLine>()
        for (rawLine in raw.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (metaTag.matches(line) && !timestampStart.containsMatchIn(line)) {
                header.add(line.trim())
                continue
            }
            val stamps = ArrayList<String>()
            var rest = line
            while (true) {
                val match = timestampStart.find(rest) ?: break
                stamps.add(match.groupValues[1])
                rest = rest.substring(match.range.last + 1)
            }
            if (stamps.isEmpty()) {
                lines.add(EditorLine(id++, "", line))
            } else {
                val text = rest.trim()
                for (stamp in stamps) lines.add(EditorLine(id++, stamp, text))
            }
        }
        // A trailing empty plain row only comes from the final newline.
        while (lines.isNotEmpty() && lines.last().timeText.isEmpty() && lines.last().text.isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }
        return EditorDocument(header, lines)
    }

    sealed interface Result {
        data class Ok(val raw: String?) : Result
        /** [lineNumber] is 1-based. */
        data class BadTimestamp(val lineNumber: Int, val text: String) : Result
    }

    /**
     * Builds the text stored in the file. Timestamps are written exactly as typed. Rows without a
     * timestamp are written as plain text lines. An empty document returns null (= remove the field).
     */
    fun toRaw(document: EditorDocument): Result {
        // No lines left = no lyrics: the stored field is removed (header tags alone are not lyrics).
        if (document.lines.isEmpty()) return Result.Ok(null)
        document.lines.forEachIndexed { index, row ->
            val time = row.timeText.trim()
            if (time.isNotEmpty() && LrcParser.parseTimestamp(time) == null) return Result.BadTimestamp(index + 1, time)
        }
        val raw = toText(document)
        return Result.Ok(if (raw.isBlank()) null else raw)
    }

    /**
     * The whole document as one text block (header, then `[time]text` / plain lines) for the paste-and-edit
     * text mode. Lenient: timestamps are NOT validated here; [fromRaw] keeps anything it cannot read as a
     * timestamp as a plain line, verbatim, so nothing is silently rewritten.
     */
    fun toText(document: EditorDocument): String {
        val out = ArrayList<String>()
        out.addAll(document.header)
        document.lines.forEach { row ->
            val time = row.timeText.trim()
            out.add(if (time.isEmpty()) row.text else "[$time]${row.text}")
        }
        return out.joinToString("\n")
    }

    /** Lines (with a valid timestamp) as the synchronized preview would use them. */
    fun previewLines(document: EditorDocument): List<LyricLine> =
        document.lines.mapNotNull { row ->
            LrcParser.parseTimestamp(row.timeText.trim())?.let { LyricLine(it, row.text) }
        }.sortedBy { it.timestampMs }
}
