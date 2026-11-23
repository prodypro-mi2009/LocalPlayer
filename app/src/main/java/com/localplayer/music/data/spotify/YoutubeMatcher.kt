package com.localplayer.music.data.spotify

import kotlin.math.abs

/** One search result of YouTube: [durationMs] is 0 when YouTube did not say. */
data class YoutubeHit(val id: String, val title: String, val channel: String, val durationMs: Long)

/**
 * Chooses, among the YouTube search results for a Spotify song, the one that is really the same song: same title,
 * the artist named in the video title or as the channel, the same version (live / remix / acoustic ... must agree
 * both ways) and, when both durations are known, the same length. When no result passes, the song is "not found" -
 * nothing is guessed. Pure Kotlin, unit-tested on the JVM.
 */
object YoutubeMatcher {
    /** Longest accepted difference between the Spotify length and the video length. */
    const val DURATION_TOLERANCE_MS = 6_000L

    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKETS = Regex("[(\\[{][^)\\]}]*[)\\]}]")

    /** Words that make a song a different version. "Remastered", "official video", "lyrics" ... are not versions. */
    private val VERSION_WORDS = listOf(
        "live", "remix", "acoustic", "instrumental", "karaoke", "cover", "sped up", "speed up", "slowed", "reverb",
        "nightcore", "8d", "demo", "radio edit", "extended", "unplugged", "orchestral", "piano version", "mashup",
        "a cappella", "acapella", "reprise", "rehearsal", "session",
    )

    private fun key(text: String): String = text.lowercase().replace(NOT_WORD, "")

    private fun spaced(text: String): String = " " + text.lowercase().replace(NOT_WORD, " ").trim() + " "

    /** The song's own name: without brackets and without a trailing " - Remastered 2011" / " - Live" part. */
    fun core(title: String): String {
        val noBrackets = title.replace(BRACKETS, " ")
        val head = noBrackets.substringBefore(" - ")
        return key(head).ifEmpty { key(title) }
    }

    /** The version words that a title states (brackets and dash parts included). */
    fun versions(title: String): Set<String> {
        val text = spaced(title)
        return VERSION_WORDS.filter { " $it " in text || (" ${it}s " in text) }.toSet()
    }

    /** Artist names of Spotify's "A, B & C" text, as keys. */
    private fun artistKeys(artist: String): List<String> =
        artist.split(',', '&', ';').map { key(it) }.filter { it.isNotEmpty() }

    private fun cleanChannel(channel: String): String =
        key(channel.removeSuffix(" - Topic").replace("VEVO", "", ignoreCase = true))

    fun isSame(track: SpotifyTrack, hit: YoutubeHit): Boolean {
        val wantedCore = core(track.title)
        if (wantedCore.isEmpty()) return false
        val hitTitle = key(hit.title)
        if (wantedCore !in hitTitle) return false
        // Same version: whatever one side states, the other must state as well.
        if (versions(track.title) != versions(hit.title)) return false
        // Artist: named in the video's title or the channel (a "Topic" channel is the artist itself).
        val artists = artistKeys(track.artist)
        if (artists.isNotEmpty()) {
            val channel = cleanChannel(hit.channel)
            val named = artists.any { it in hitTitle || (channel.isNotEmpty() && (channel.contains(it) || it.contains(channel))) }
            if (!named) return false
        }
        val wanted = track.durationMs
        if (wanted != null && wanted > 0 && hit.durationMs > 0 && abs(wanted - hit.durationMs) > DURATION_TOLERANCE_MS) return false
        return true
    }

    /** The best matching result, or null (= not found). Prefers the artist's "Topic" channel, then "official audio". */
    fun best(track: SpotifyTrack, hits: List<YoutubeHit>): YoutubeHit? {
        val ok = hits.filter { isSame(track, it) }
        if (ok.isEmpty()) return null
        return ok.withIndex().maxByOrNull { (index, hit) ->
            var score = 0
            if (hit.channel.endsWith(" - Topic")) score += 30
            if (spaced(hit.title).contains(" official audio ")) score += 15
            val wanted = track.durationMs
            if (wanted != null && wanted > 0 && hit.durationMs > 0) score += (10 - abs(wanted - hit.durationMs) / 1000).toInt().coerceAtLeast(0)
            score * 10 - index // equal scores: the earlier result wins
        }?.value
    }
}
