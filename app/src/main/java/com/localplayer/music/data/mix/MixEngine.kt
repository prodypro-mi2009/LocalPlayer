package com.localplayer.music.data.mix

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class MixStrategy(val key: String) {
    MOST_PLAYED("most_played"),
    FORGOTTEN("forgotten"),
    RANDOM("random"),
    SMART("smart");

    companion object {
        fun fromKey(key: String?): MixStrategy = entries.firstOrNull { it.key == key } ?: SMART
    }
}

/** What the user can tune. Always [normalized] before use, so a damaged saved value can never break the Mix. */
data class MixSettings(
    val strategy: MixStrategy = MixStrategy.SMART,
    /** Number of songs in the Mix. */
    val size: Int = 30,
    /** Look-back window for play counts, in days (0 = all time). Used by "Most played" and the smart mix. */
    val windowDays: Int = 30,
    /** 0..100: how much recently listened songs count (smart mix). */
    val recentWeight: Int = 40,
    /** 0..100: how strongly the same artist / album is kept from repeating. */
    val variety: Int = 60,
    /** Songs heard within this many days are kept out of the Mix (0 = no filtering). */
    val avoidRecentDays: Int = 1,
) {
    fun normalized() = copy(
        size = size.coerceIn(MIN_SIZE, MAX_SIZE),
        windowDays = if (windowDays in WINDOWS) windowDays else 30,
        recentWeight = recentWeight.coerceIn(0, 100),
        variety = variety.coerceIn(0, 100),
        avoidRecentDays = avoidRecentDays.coerceIn(0, MAX_AVOID_DAYS),
    )

    fun serialize(): String = normalized().let {
        "${it.strategy.key};${it.size};${it.windowDays};${it.recentWeight};${it.variety};${it.avoidRecentDays}"
    }

    companion object {
        const val MIN_SIZE = 10
        const val MAX_SIZE = 100
        const val MAX_AVOID_DAYS = 14
        val WINDOWS = listOf(7, 30, 90, 365, 0)

        fun parse(raw: String?): MixSettings {
            val p = raw?.split(';') ?: return MixSettings()
            if (p.size != 6) return MixSettings()
            val d = MixSettings()
            return MixSettings(
                strategy = MixStrategy.fromKey(p[0]),
                size = p[1].toIntOrNull() ?: d.size,
                windowDays = p[2].toIntOrNull() ?: d.windowDays,
                recentWeight = p[3].toIntOrNull() ?: d.recentWeight,
                variety = p[4].toIntOrNull() ?: d.variety,
                avoidRecentDays = p[5].toIntOrNull() ?: d.avoidRecentDays,
            ).normalized()
        }
    }
}

/** A visible (not hidden, available) song the Mix may use. Any metadata field may be missing. */
data class MixCandidate(
    val id: Long,
    val uri: String,
    val title: String,
    val artistKey: String?,
    val albumKey: String?,
    val genreKey: String?,
    val durationMs: Long,
    /** MediaStore "date added" (seconds) or milliseconds; 0 = unknown. */
    val dateAdded: Long,
)

/** Local listening data of one song. Songs without any history are simply absent from the map. */
data class MixStats(val windowPlays: Int, val totalPlays: Int, val lastPlayedAt: Long?)

enum class MixNotice {
    NONE,
    /** Too little listening data for the chosen strategy: the Mix uses new and random songs instead. */
    NO_HISTORY,
    /** Some history exists but not enough for the whole Mix: the rest is filled with other songs. */
    PARTIAL_HISTORY,
    EMPTY_LIBRARY,
    /** The library has fewer eligible songs than the requested size. */
    FEWER_THAN_REQUESTED,
}

class MixResult(val uris: List<String>, val notice: MixNotice, val strategy: MixStrategy)

/**
 * Builds the Your Mix list. Pure and deterministic for a given seed, so it is unit tested; everything is local.
 * Missing metadata never excludes a song: a missing genre skips the genre variety rule, a missing artist/album
 * skips that cap, a missing play history counts as "never played".
 */
object MixEngine {
    private const val DAY_MS = 86_400_000L
    private const val MIN_HISTORY_SONGS = 5

    private class Scored(val c: MixCandidate, val score: Double)

    fun generate(
        candidates: List<MixCandidate>,
        stats: Map<String, MixStats>,
        settings: MixSettings,
        nowMs: Long,
        seed: Long,
    ): MixResult {
        val s = settings.normalized()
        if (candidates.isEmpty()) return MixResult(emptyList(), MixNotice.EMPTY_LIBRARY, s.strategy)
        val rnd = Random(seed)

        val unique = dedupe(candidates, stats)
        val target = min(s.size, unique.size)
        val eligible = withoutRecentlyHeard(unique, stats, s.avoidRecentDays, nowMs, target)

        var notice = MixNotice.NONE
        val withHistory = eligible.count { (stats[it.uri]?.totalPlays ?: 0) > 0 || stats[it.uri]?.lastPlayedAt != null }

        val ranked: List<Scored> = when (s.strategy) {
            MixStrategy.RANDOM -> eligible.map { Scored(it, rnd.nextDouble()) }
            MixStrategy.FORGOTTEN -> {
                if (withHistory == 0) { notice = MixNotice.NO_HISTORY; freshAndRandom(eligible, nowMs, rnd) }
                else eligible.map { Scored(it, forgottenScore(it, stats[it.uri], nowMs, rnd)) }
            }
            MixStrategy.MOST_PLAYED -> {
                val played = eligible.filter { (stats[it.uri]?.windowPlays ?: 0) > 0 }
                when {
                    played.isEmpty() -> { notice = MixNotice.NO_HISTORY; freshAndRandom(eligible, nowMs, rnd) }
                    else -> {
                        if (played.size < target) notice = MixNotice.PARTIAL_HISTORY
                        // Played songs first (most played on top), then, if needed, a small fresh/random tail.
                        val top = played.map { c ->
                            val st = stats.getValue(c.uri)
                            Scored(c, 1_000_000.0 + st.windowPlays * 1_000.0 + st.totalPlays + rnd.nextDouble() * 0.5)
                        }
                        val playedUris = played.mapTo(HashSet()) { it.uri }
                        top + freshAndRandom(eligible.filter { it.uri !in playedUris }, nowMs, rnd)
                    }
                }
            }
            MixStrategy.SMART -> {
                if (withHistory < MIN_HISTORY_SONGS) { notice = MixNotice.NO_HISTORY; freshAndRandom(eligible, nowMs, rnd) }
                else {
                    val maxW = max(1, eligible.maxOf { stats[it.uri]?.windowPlays ?: 0 })
                    val rw = s.recentWeight / 100.0
                    eligible.map { c ->
                        val st = stats[c.uri]
                        val freq = ln(1.0 + (st?.windowPlays ?: 0)) / ln(1.0 + maxW)
                        val closeness = st?.lastPlayedAt?.let { exp(-max(0.0, (nowMs - it).toDouble() / DAY_MS) / 7.0) } ?: 0.0
                        val novelty = if (st == null) 1.0 else 0.0
                        Scored(c, (1 - rw) * freq + rw * closeness + 0.10 * novelty + 0.15 * rnd.nextDouble())
                    }
                }
            }
        }

        val artistCap = max(1, (s.size * (1.0 - 0.9 * s.variety / 100.0)).roundToInt())
        val albumCap = max(1, (artistCap + 1) / 2)
        val genrePenalty = if (s.strategy == MixStrategy.SMART || s.strategy == MixStrategy.FORGOTTEN) 1.0 - 0.15 * s.variety / 100.0 else 1.0
        val picked = pick(ranked, target, artistCap, albumCap, genrePenalty)
        val ordered = spread(picked)

        if (notice == MixNotice.NONE && unique.size < s.size) notice = MixNotice.FEWER_THAN_REQUESTED
        return MixResult(ordered.map { it.uri }, notice, s.strategy)
    }

    /** The same song stored twice (same title, artist and length) appears once; the one with more plays wins. */
    internal fun dedupe(candidates: List<MixCandidate>, stats: Map<String, MixStats>): List<MixCandidate> {
        val best = LinkedHashMap<String, MixCandidate>()
        for (c in candidates) {
            val key = "${norm(c.title)}|${norm(c.artistKey)}|${c.durationMs / 2000}"
            val current = best[key]
            if (current == null) { best[key] = c; continue }
            val a = stats[c.uri]?.totalPlays ?: 0
            val b = stats[current.uri]?.totalPlays ?: 0
            if (a > b || (a == b && c.id < current.id)) best[key] = c
        }
        return best.values.toList()
    }

    private fun norm(s: String?) = s.orEmpty().trim().lowercase()

    private fun withoutRecentlyHeard(
        songs: List<MixCandidate>,
        stats: Map<String, MixStats>,
        avoidDays: Int,
        now: Long,
        target: Int,
    ): List<MixCandidate> {
        if (avoidDays <= 0) return songs
        val cutoff = now - avoidDays * DAY_MS
        val (fresh, recent) = songs.partition { (stats[it.uri]?.lastPlayedAt ?: Long.MIN_VALUE) < cutoff }
        if (fresh.size >= target) return fresh
        // Not enough songs left: bring back the ones heard longest ago, so the Mix is never short for no reason.
        return fresh + recent.sortedBy { stats[it.uri]?.lastPlayedAt ?: 0L }.take(target - fresh.size)
    }

    private fun addedMs(c: MixCandidate): Long = when {
        c.dateAdded <= 0L -> 0L
        c.dateAdded < 100_000_000_000L -> c.dateAdded * 1000L
        else -> c.dateAdded
    }

    /** Fallback ranking when there is no listening data: mostly random, newer songs a little ahead. */
    private fun freshAndRandom(songs: List<MixCandidate>, now: Long, rnd: Random): List<Scored> =
        songs.map { c ->
            val age = if (addedMs(c) == 0L) 1.0 else min(1.0, max(0.0, (now - addedMs(c)).toDouble() / (365 * DAY_MS)))
            Scored(c, 0.6 * rnd.nextDouble() + 0.4 * (1.0 - age))
        }

    private fun forgottenScore(c: MixCandidate, st: MixStats?, now: Long, rnd: Random): Double {
        val jitter = 0.1 * rnd.nextDouble()
        if (st == null || (st.totalPlays == 0 && st.lastPlayedAt == null)) {
            // Never played is NOT automatically a good suggestion: it ranks below songs that were liked and then neglected.
            val ageDays = if (addedMs(c) == 0L) 0.0 else max(0.0, (now - addedMs(c)).toDouble() / DAY_MS)
            return 0.35 + 0.15 * min(1.0, ageDays / 365.0) + jitter
        }
        val days = st.lastPlayedAt?.let { max(0.0, (now - it).toDouble() / DAY_MS) } ?: 180.0
        val affinity = 0.5 + 0.5 * min(1.0, ln(1.0 + st.totalPlays) / ln(11.0))
        return min(1.0, days / 365.0) * affinity + jitter
    }

    private fun group(key: String?): String? = key?.trim()?.takeIf { it.isNotEmpty() }

    private fun pick(ranked: List<Scored>, target: Int, artistCap: Int, albumCap: Int, genrePenalty: Double): List<MixCandidate> {
        val remaining = ranked.sortedByDescending { it.score }.toMutableList()
        val out = ArrayList<MixCandidate>(target)
        val artists = HashMap<String, Int>()
        val albums = HashMap<String, Int>()
        val genres = HashMap<String, Int>()
        while (out.size < target && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestVal = Double.NEGATIVE_INFINITY
            for (i in remaining.indices) {
                val c = remaining[i].c
                val a = group(c.artistKey)
                val al = group(c.albumKey)
                if (a != null && (artists[a] ?: 0) >= artistCap) continue
                if (al != null && (albums[al] ?: 0) >= albumCap) continue
                val g = group(c.genreKey)
                val v = remaining[i].score * (if (g != null && genrePenalty < 1.0) genrePenalty.pow(genres[g] ?: 0) else 1.0)
                if (v > bestVal) { bestVal = v; bestIdx = i }
            }
            if (bestIdx < 0) break
            val c = remaining.removeAt(bestIdx).c
            out.add(c)
            group(c.artistKey)?.let { artists.merge(it, 1, Int::plus) }
            group(c.albumKey)?.let { albums.merge(it, 1, Int::plus) }
            group(c.genreKey)?.let { genres.merge(it, 1, Int::plus) }
        }
        // Caps blocked everything that was left: fill up in rank order rather than return a short Mix.
        var i = 0
        while (out.size < target && i < remaining.size) out.add(remaining[i++].c)
        return out
    }

    /** Keeps the rank order but avoids the same artist twice in a row when another song can go next. */
    internal fun spread(list: List<MixCandidate>): List<MixCandidate> {
        val rest = list.toMutableList()
        val out = ArrayList<MixCandidate>(list.size)
        while (rest.isNotEmpty()) {
            val prev = out.lastOrNull()?.let { group(it.artistKey) }
            val idx = rest.indexOfFirst { prev == null || group(it.artistKey) == null || group(it.artistKey) != prev }
            out.add(rest.removeAt(if (idx >= 0) idx else 0))
        }
        return out
    }
}

/** When the saved Mix is thrown away and rebuilt (so it is not recomputed on every screen or recomposition). */
object MixRefresh {
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    const val NEW_PLAYS_FOR_REFRESH = 10

    class Snapshot(
        val settingsKey: String,
        val generatedAt: Long,
        val playsAtGeneration: Int,
        val uris: List<String>,
        val notice: MixNotice = MixNotice.NONE,
    ) {
        /** One header line "settings|time|plays|notice", then one URI per line. */
        fun serialize(): String =
            (listOf("$settingsKey|$generatedAt|$playsAtGeneration|${notice.name}") + uris).joinToString("\n")

        companion object {
            fun parse(raw: String?): Snapshot? {
                if (raw.isNullOrBlank()) return null
                val lines = raw.split('\n')
                val head = lines[0].split('|')
                if (head.size != 4) return null
                return Snapshot(
                    settingsKey = head[0],
                    generatedAt = head[1].toLongOrNull() ?: return null,
                    playsAtGeneration = head[2].toIntOrNull() ?: return null,
                    uris = lines.drop(1).filter { it.isNotEmpty() },
                    notice = MixNotice.entries.firstOrNull { it.name == head[3] } ?: MixNotice.NONE,
                )
            }
        }
    }

    fun needsRefresh(
        snapshot: Snapshot?,
        settings: MixSettings,
        nowMs: Long,
        totalPlaysNow: Int,
        stillVisibleUris: Set<String>,
        force: Boolean,
    ): Boolean {
        if (force || snapshot == null) return true
        if (snapshot.settingsKey != settings.serialize()) return true
        if (nowMs - snapshot.generatedAt >= MAX_AGE_MS || nowMs < snapshot.generatedAt) return true
        if (totalPlaysNow - snapshot.playsAtGeneration >= NEW_PLAYS_FOR_REFRESH) return true
        if (snapshot.uris.isEmpty()) return true
        // Songs that were hidden/deleted since: rebuild once less than 70 % of the Mix is still usable.
        val alive = snapshot.uris.count { it in stillVisibleUris }
        return alive * 10 < snapshot.uris.size * 7
    }
}
