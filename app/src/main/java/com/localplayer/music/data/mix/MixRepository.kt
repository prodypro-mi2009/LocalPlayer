package com.localplayer.music.data.mix

import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.stats.TimeRanges
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class MixOutput(val tracks: List<TrackListItem>, val notice: MixNotice, val generatedAt: Long, val settings: MixSettings)

/**
 * Loads Your Mix: the saved Mix when it is still good (same settings, younger than a day, fewer than
 * [MixRefresh.NEW_PLAYS_FOR_REFRESH] new plays, songs still visible), otherwise a freshly generated one. Cheap to call
 * from any screen: a saved Mix costs two small queries and no scoring work.
 */
class MixRepository(private val database: AppDatabase, private val settings: AppSettings) {
    private val tracks = database.trackDao()
    private val listening = database.listeningDao()
    private val lock = Mutex()

    suspend fun load(force: Boolean): MixOutput = lock.withLock {
        val s = settings.mixSettings.value
        val now = System.currentTimeMillis()
        val totalPlays = listening.totalPlays()
        val visible = tracks.visibleUris().toHashSet()
        var snapshot = settings.mixSnapshot
        if (MixRefresh.needsRefresh(snapshot, s, now, totalPlays, visible, force)) {
            val result = generate(s, now)
            snapshot = MixRefresh.Snapshot(s.serialize(), now, totalPlays, result.uris, result.notice)
            settings.mixSnapshot = snapshot
        }
        val snap = snapshot!!
        // Songs hidden/removed since the Mix was made simply drop out (the query only returns visible songs).
        val byUri = tracks.itemsByUris(snap.uris).associateBy { it.uri }
        MixOutput(snap.uris.mapNotNull { byUri[it] }, snap.notice, snap.generatedAt, s)
    }

    private suspend fun generate(s: MixSettings, now: Long): MixResult {
        val candidates = tracks.mixCandidates()
        val fromDay = if (s.windowDays == 0) 0 else TimeRanges.dayKey(LocalDate.now().minusDays(s.windowDays.toLong()))
        val window = listening.playsSince(fromDay).associate { it.trackUri to it.plays }
        val totals = listening.totals().associateBy { it.trackUri }
        val legacy = listening.legacyLastPlayed().associate { it.trackUri to it.lastAt }
        val uris = HashSet<String>().apply { addAll(window.keys); addAll(totals.keys); addAll(legacy.keys) }
        val stats = HashMap<String, MixStats>(uris.size)
        for (uri in uris) {
            val last = listOfNotNull(totals[uri]?.lastAt, legacy[uri]).maxOrNull()
            stats[uri] = MixStats(window[uri] ?: 0, totals[uri]?.totalPlays ?: 0, last)
        }
        return MixEngine.generate(candidates, stats, s, now, seed = now)
    }
}
