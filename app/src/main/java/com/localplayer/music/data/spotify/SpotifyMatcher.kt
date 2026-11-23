package com.localplayer.music.data.spotify

import com.localplayer.music.data.lyrics.LrcLibMatcher
import kotlin.math.abs

/** What the matcher needs to know about a song of the library. */
data class LibraryEntry(val uri: String, val title: String, val artists: List<String>, val durationMs: Long)

/** [libraryUri] = the song of the library that is the same as [track], or null when the library does not have it. */
data class SpotifyMatch(val track: SpotifyTrack, val libraryUri: String?)

/** Finds which songs of a Spotify list are already in the library (same title and a shared artist; nothing is guessed). */
object SpotifyMatcher {
    fun match(tracks: List<SpotifyTrack>, library: List<LibraryEntry>): List<SpotifyMatch> {
        val byTitle = HashMap<String, MutableList<Pair<LibraryEntry, List<String>>>>()
        for (entry in library) {
            val key = LrcLibMatcher.normalize(entry.title)
            if (key.isEmpty()) continue
            byTitle.getOrPut(key) { ArrayList() } += entry to entry.artists.map { LrcLibMatcher.normalize(it) }.filter { it.isNotEmpty() }
        }
        return tracks.map { track ->
            val candidates = byTitle[LrcLibMatcher.normalize(track.title)].orEmpty()
            val wanted = track.artist.split(',', '&').map { LrcLibMatcher.normalize(it) }.filter { it.isNotEmpty() }
            val sameArtist = candidates.filter { (_, artists) -> sharesArtist(wanted, artists) }
            val best = sameArtist.minByOrNull { (entry, _) -> track.durationMs?.let { abs(it - entry.durationMs) } ?: 0L }
            SpotifyMatch(track, best?.first?.uri)
        }
    }

    private fun sharesArtist(wanted: List<String>, have: List<String>): Boolean =
        wanted.any { w -> have.any { h -> h == w || h.contains(w) || w.contains(h) } }
}
