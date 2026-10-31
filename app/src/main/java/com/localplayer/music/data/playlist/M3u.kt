package com.localplayer.music.data.playlist

import kotlin.math.abs

/** A song of this library, as far as matching a playlist line needs to know it. */
class M3uCandidate(val uri: String, val name: String?, val title: String?, val artist: String?, val durationMs: Long)

/** One line of an .m3u / .m3u8 playlist. [path] is whatever the file says (a path, a file name or a file:// address). */
class M3uEntry(val path: String, val title: String?, val artist: String?, val durationSec: Int)

/**
 * Reads and writes .m3u / .m3u8 playlists (UTF-8). Pure Kotlin, tested on the JVM.
 *
 * Android gives an app no real file paths for the music library, so an export writes each song's FILE NAME (a relative
 * path) together with #EXTINF (duration, "Artist - Title"). Other players find the files when the playlist sits in
 * the music folder; this app finds them again by [M3uMatcher], by name and by title + artist.
 */
object M3u {
    const val MAX_ENTRIES = 50_000
    private const val MAX_LINE = 4_096

    fun parse(text: String): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var title: String? = null
        var artist: String? = null
        var seconds = 0
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.length > MAX_LINE) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val rest = line.substring(8)
                    val comma = rest.indexOf(',')
                    val head = if (comma >= 0) rest.substring(0, comma) else rest
                    val label = if (comma >= 0) rest.substring(comma + 1).trim() else ""
                    seconds = head.trim().takeWhile { it.isDigit() || it == '-' }.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val split = label.indexOf(" - ")
                    if (split > 0) {
                        artist = label.substring(0, split).trim().ifEmpty { null }
                        title = label.substring(split + 3).trim().ifEmpty { null }
                    } else {
                        artist = null
                        title = label.ifEmpty { null }
                    }
                }
                continue
            }
            out += M3uEntry(cleanPath(line), title, artist, seconds)
            title = null; artist = null; seconds = 0
            if (out.size >= MAX_ENTRIES) break
        }
        return out
    }

    fun write(entries: List<M3uEntry>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (e in entries) {
            val label = listOfNotNull(e.artist?.takeIf { it.isNotBlank() }, e.title?.takeIf { it.isNotBlank() }).joinToString(" - ")
            val one = label.replace('\n', ' ').replace('\r', ' ')
            sb.append("#EXTINF:").append(e.durationSec.coerceAtLeast(0)).append(',').append(one).append('\n')
            sb.append(e.path.replace('\n', ' ').replace('\r', ' ')).append('\n')
        }
        return sb.toString()
    }

    /** "C:\Music\a b.mp3", "/sdcard/Music/a b.mp3" or "file:///sdcard/Music/a%20b.mp3" -> the last part, "a b.mp3". */
    fun fileName(path: String): String {
        val p = cleanPath(path)
        return p.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun cleanPath(line: String): String {
        if (!line.startsWith("file:", ignoreCase = true)) return line
        val raw = line.substring(5).trimStart('/')
        return runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
    }
}

/** Finds the song of this library that a playlist line stands for. Not found or ambiguous = null (reported, never guessed). */
class M3uMatcher(current: List<M3uCandidate>) {
    private val byName = HashMap<String, MutableList<M3uCandidate>>()
    private val byStem = HashMap<String, MutableList<M3uCandidate>>()
    private val byTitle = HashMap<String, MutableList<M3uCandidate>>()

    init {
        for (t in current) {
            norm(t.name)?.let { byName.getOrPut(it) { ArrayList(1) } += t }
            norm(t.name?.substringBeforeLast('.'))?.let { byStem.getOrPut(it) { ArrayList(1) } += t }
            norm(t.title)?.let { byTitle.getOrPut(it) { ArrayList(1) } += t }
        }
    }

    fun match(e: M3uEntry): String? {
        val name = M3u.fileName(e.path)
        pick(byName[norm(name)].orEmpty(), e.durationSec)?.let { return it }
        pick(byStem[norm(name.substringBeforeLast('.'))].orEmpty(), e.durationSec)?.let { return it }
        val title = norm(e.title) ?: norm(name.substringBeforeLast('.'))
        if (title != null) {
            val artist = norm(e.artist)
            val list = byTitle[title].orEmpty().let { l -> if (artist != null) l.filter { norm(it.artist) == artist } else l }
            pick(list, e.durationSec)?.let { return it }
        }
        return null
    }

    /** The only candidate, or the only one whose duration is within 2 s of the line's duration. */
    private fun pick(list: List<M3uCandidate>, seconds: Int): String? {
        if (list.size == 1) return list[0].uri
        if (list.isEmpty() || seconds <= 0) return null
        return list.filter { it.durationMs > 0 && abs(it.durationMs - seconds * 1000L) <= 2_000 }.singleOrNull()?.uri
    }

    private fun norm(s: String?): String? =
        s?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}
