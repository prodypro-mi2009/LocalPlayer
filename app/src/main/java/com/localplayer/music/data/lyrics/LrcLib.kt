package com.localplayer.music.data.lyrics

import kotlin.math.abs

/** One LRCLIB result (https://lrclib.net/api, fields as documented: trackName, artistName, albumName, duration in seconds, ...). */
data class LrcLibResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
) {
    val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasPlain: Boolean get() = !plainLyrics.isNullOrBlank()
}

enum class LyricsMatch {
    /** Same title and artist, and the length agrees (within 3 s): safe to use. */
    GOOD,
    /** Same title and artist, the length is unknown or a few seconds off. */
    POSSIBLE,
    /** Same title and artist but the length differs a lot: probably another version (live, remix, extended). */
    DIFFERENT_VERSION,
}

data class RankedLyrics(val result: LrcLibResult, val match: LyricsMatch, val durationDiffSec: Double?)

/**
 * Pure ranking of LRCLIB results for one song. Results that are not the same song are dropped, results that are
 * the same title/artist but another length are kept only as [LyricsMatch.DIFFERENT_VERSION] (shown with a warning,
 * never preselected), and synchronized lyrics rank first among equally good matches.
 */
object LrcLibMatcher {
    private const val GOOD_SEC = 3.0
    private const val POSSIBLE_SEC = 10.0

    fun normalize(text: String?): String =
        text.orEmpty().lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")      // (feat. X), [Remastered]
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun sameText(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    fun rank(results: List<LrcLibResult>, title: String, artist: String?, durationMs: Long): List<RankedLyrics> {
        val t = normalize(title)
        val a = normalize(artist)
        val ours = if (durationMs > 0) durationMs / 1000.0 else null
        return results
            .filter { !it.instrumental && (it.hasSynced || it.hasPlain) }
            .distinctBy { it.id }
            .mapNotNull { r ->
                val titleOk = normalize(r.trackName).let { it == t || sameText(it, t) }
                // A missing artist on our side must not reject everything, but a given one has to agree.
                val artistOk = a.isEmpty() || sameText(normalize(r.artistName), a)
                if (!titleOk || !artistOk) return@mapNotNull null
                val diff = if (ours != null && r.durationSec > 0) abs(ours - r.durationSec) else null
                val exactTitle = normalize(r.trackName) == t
                val match = when {
                    diff == null -> LyricsMatch.POSSIBLE
                    diff <= GOOD_SEC && exactTitle -> LyricsMatch.GOOD
                    diff <= POSSIBLE_SEC -> LyricsMatch.POSSIBLE
                    else -> LyricsMatch.DIFFERENT_VERSION
                }
                RankedLyrics(r, match, diff)
            }
            .sortedWith(
                compareBy<RankedLyrics> { it.match.ordinal }
                    .thenBy { if (it.result.hasSynced) 0 else 1 }
                    .thenBy { it.durationDiffSec ?: 1_000.0 },
            )
    }

    /** True when the search found only instrumentals (so "no lyrics exist" is the honest answer). */
    fun onlyInstrumental(results: List<LrcLibResult>) = results.isNotEmpty() && results.all { it.instrumental }
}
