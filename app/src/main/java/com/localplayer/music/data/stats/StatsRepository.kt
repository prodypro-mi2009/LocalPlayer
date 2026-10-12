package com.localplayer.music.data.stats

import androidx.room.withTransaction
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.ListeningDayEntity
import com.localplayer.music.data.db.TopTrackRow
import kotlinx.coroutines.flow.Flow

/** Listening statistics (local only): written by the playback service, read by Time Usage, Top 20 and Your Mix. */
class StatsRepository(private val database: AppDatabase) {
    private val dao = database.listeningDao()

    /** Adds real listening time / plays to the (day, song) row, creating it on first use. Additive and atomic. */
    suspend fun record(day: Int, uri: String, listenedMs: Long, plays: Int, nowMs: Long) {
        database.withTransaction {
            if (dao.addTo(day, uri, listenedMs, plays, nowMs) == 0) {
                val inserted = dao.insert(ListeningDayEntity(day, uri, listenedMs, plays, nowMs))
                if (inserted == -1L) dao.addTo(day, uri, listenedMs, plays, nowMs)
            }
        }
    }

    fun listenedMs(range: DayRange): Flow<Long> = dao.observeListenedMs(range.fromKey, range.toKey)

    fun plays(range: DayRange): Flow<Int> = dao.observePlays(range.fromKey, range.toKey)

    fun top20(range: DayRange): Flow<List<TopTrackRow>> = dao.observeTop20(range.fromKey, range.toKey)
}
