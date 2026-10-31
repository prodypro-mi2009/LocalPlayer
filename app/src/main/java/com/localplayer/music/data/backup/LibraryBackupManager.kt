package com.localplayer.music.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.localplayer.music.data.artwork.PlaylistCoverStore
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.FavoriteEntity
import com.localplayer.music.data.db.HistoryEntity
import com.localplayer.music.data.db.ListeningDayEntity
import com.localplayer.music.data.db.PlaylistEntity
import com.localplayer.music.data.db.PlaylistTrackEntity
import com.localplayer.music.data.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What [LibraryBackupManager.collect] hands to the archive writer. */
class CollectedLibrary(val data: LibraryData, val covers: Map<String, File>)

/** What an import of library data really did (numbers of rows written; skipped = entries whose song was not found). */
class LibraryApplyResult(
    val playlistsAdded: Int,
    val playlistsMerged: Int,
    val playlistTracks: Int,
    val favorites: Int,
    val history: Int,
    val days: Int,
    val overrides: Int,
    val hidden: Int,
    val excluded: Int,
    val skipped: Int,
)

/**
 * Reads the library data (playlists, favourites, history, rules) for a backup and writes it back on import.
 * Idle unless the user starts an export or import (or the opt-in automatic backup runs): nothing here runs at startup.
 */
class LibraryBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val library: LibraryRepository,
    private val covers: PlaylistCoverStore,
) {
    private val dao get() = database.backupDao()

    // ---- export -----------------------------------------------------------------------------------------------

    suspend fun collect(): CollectedLibrary = withContext(Dispatchers.IO) {
        val rows = dao.matchRows().associateBy { it.uri }
        val refs = ArrayList<TrackRef>()
        val index = HashMap<String, Int>()
        // Songs the library does not know any more (stale rows) are left out: they could not be found again anyway.
        fun idx(uri: String): Int? {
            index[uri]?.let { return it }
            val r = rows[uri] ?: return null
            refs += TrackRef(uri, r.displayName, r.title, r.artist, r.durationMs, r.sizeBytes)
            return (refs.size - 1).also { index[uri] = it }
        }

        val playlistTracks = dao.playlistTracks().groupBy { it.playlistId }
        val coverFiles = LinkedHashMap<String, File>()
        val playlists = dao.playlists().map { p ->
            val items = playlistTracks[p.id].orEmpty().mapNotNull { t -> idx(t.trackUri)?.let { PlaylistItemData(it, t.position, t.addedAt) } }
            val cover = p.coverFile?.takeIf { BackupFormat.isCoverName(it) && covers.file(it).isFile && coverFiles.size < BackupFormat.MAX_COVERS }
            if (cover != null) coverFiles[cover] = covers.file(cover)
            PlaylistData(
                p.name, p.createdAt, p.updatedAt, p.sourceFolderKey, p.sourceFolderLabel, p.sourceIncludesSubfolders, cover, items,
            )
        }
        val favorites = dao.favorites().mapNotNull { f -> idx(f.trackUri)?.let { FavoriteData(it, f.addedAt) } }
        val history = dao.history().mapNotNull { h -> idx(h.trackUri)?.let { HistoryData(it, h.playedAt) } }
        val days = dao.days().mapNotNull { d -> idx(d.trackUri)?.let { DayData(d.day, it, d.listenedMs, d.plays, d.lastListenedAt) } }
        val overrides = dao.overrides().mapNotNull { o -> idx(o.trackUri)?.let { OverrideData(it, o.primaryArtist) } }
        val hidden = dao.hidden().mapNotNull { h -> idx(h.trackUri)?.let { HiddenData(it, h.hiddenAt) } }
        val excluded = dao.excluded().map { ExcludedData(it.prefix, it.excludedAt) }
        CollectedLibrary(LibraryData(refs, playlists, favorites, history, days, overrides, hidden, excluded), coverFiles)
    }

    // ---- import: matching ----------------------------------------------------------------------------------------

    suspend fun matcher(): TrackMatcher = withContext(Dispatchers.IO) {
        TrackMatcher(dao.matchRows().map { CurrentTrack(it.uri, it.displayName, it.title, it.artist, it.durationMs, it.sizeBytes) })
    }

    // ---- import: apply ---------------------------------------------------------------------------------------------

    /**
     * Writes the [selected] sections. [resolved] maps every backup song to a song of this library (null = not found,
     * skipped). [replace] = false adds to what is here; true replaces the selected sections.
     * Playlists, favourites and history are written in ONE database transaction: on any error nothing of them changes.
     */
    suspend fun apply(
        data: LibraryData,
        resolved: List<String?>,
        coverFiles: Map<String, File>,
        selected: Set<String>,
        replace: Boolean,
    ): LibraryApplyResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val createdCovers = ArrayList<File>()
        val oldCovers = ArrayList<String>()
        var playlistsAdded = 0
        var playlistsMerged = 0
        var playlistTracks = 0
        var favorites = 0
        var history = 0
        var days = 0
        var skipped = 0

        try {
            database.withTransaction {
                if (LibrarySection.PLAYLISTS in selected && data.playlists.isNotEmpty()) {
                    val existing = dao.playlists()
                    val tracksOf = dao.playlistTracks().groupBy { it.playlistId }
                    if (replace) {
                        existing.forEach { p -> p.coverFile?.let { oldCovers += it } }
                        dao.deleteAllPlaylists()
                    }
                    val byName: Map<String, PlaylistEntity> =
                        if (replace) emptyMap() else existing.associateBy { it.name.trim().lowercase() }
                    for (p in data.playlists) {
                        val pairs = p.items.mapNotNull { item ->
                            val uri = resolved.getOrNull(item.track)
                            if (uri == null) { skipped++; null } else uri to item
                        }.distinctBy { it.first }
                        val match = byName[p.name.trim().lowercase()]
                        if (match != null) {
                            playlistsMerged++
                            // A playlist that mirrors a folder follows the folder; adding songs by hand is not allowed there.
                            if (match.sourceFolderKey == null) {
                                val have = tracksOf[match.id].orEmpty().map { it.trackUri }.toSet()
                                var pos = (tracksOf[match.id].orEmpty().maxOfOrNull { it.position } ?: -1) + 1
                                val rows = pairs.filter { it.first !in have }.map { (uri, item) ->
                                    PlaylistTrackEntity(match.id, uri, pos++, item.addedAt.takeIf { it > 0 } ?: now)
                                }
                                playlistTracks += dao.insertPlaylistTracks(rows).count { it != -1L }
                            }
                            if (match.coverFile == null) attachCover(match.id, p.cover, coverFiles, createdCovers)
                        } else {
                            playlistsAdded++
                            val id = dao.insertPlaylist(
                                PlaylistEntity(
                                    name = p.name.trim(),
                                    createdAt = p.createdAt.takeIf { it > 0 } ?: now,
                                    updatedAt = now,
                                    sourceFolderKey = p.sourceFolderKey,
                                    sourceFolderLabel = p.sourceFolderLabel,
                                    sourceIncludesSubfolders = p.sourceIncludesSubfolders,
                                ),
                            )
                            var pos = 0
                            val rows = pairs.map { (uri, item) -> PlaylistTrackEntity(id, uri, pos++, item.addedAt.takeIf { it > 0 } ?: now) }
                            playlistTracks += dao.insertPlaylistTracks(rows).count { it != -1L }
                            attachCover(id, p.cover, coverFiles, createdCovers)
                        }
                    }
                }

                if (LibrarySection.FAVORITES in selected && data.favorites.isNotEmpty()) {
                    if (replace) dao.deleteAllFavorites()
                    val rows = data.favorites.mapNotNull { f ->
                        val uri = resolved.getOrNull(f.track)
                        if (uri == null) { skipped++; null } else FavoriteEntity(uri, f.addedAt.takeIf { it > 0 } ?: now)
                    }.distinctBy { it.trackUri }
                    favorites += dao.insertFavorites(rows).count { it != -1L }
                }

                if (LibrarySection.HISTORY in selected && (data.history.isNotEmpty() || data.days.isNotEmpty())) {
                    if (replace) { dao.deleteAllHistory(); dao.deleteAllDays() }
                    val seen: HashSet<Pair<String, Long>> =
                        if (replace) HashSet() else dao.history().mapTo(HashSet()) { it.trackUri to it.playedAt }
                    val plays = data.history.mapNotNull { h ->
                        val uri = resolved.getOrNull(h.track)
                        if (uri == null) { skipped++; null } else uri to h.playedAt
                    }.filter { seen.add(it) }
                    history += dao.insertHistory(plays.map { HistoryEntity(trackUri = it.first, playedAt = it.second) }).count { it != -1L }

                    val merged = LinkedHashMap<Pair<Int, String>, ListeningDayEntity>()
                    if (!replace) dao.days().forEach { merged[it.day to it.trackUri] = it }
                    val before = merged.toMap()
                    for (d in data.days) {
                        val uri = resolved.getOrNull(d.track)
                        if (uri == null) { skipped++; continue }
                        val key = d.day to uri
                        val old = merged[key]
                        merged[key] = if (old == null) ListeningDayEntity(d.day, uri, d.listenedMs, d.plays, d.lastListenedAt)
                        else ListeningDayEntity(
                            d.day, uri, maxOf(old.listenedMs, d.listenedMs), maxOf(old.plays, d.plays), maxOf(old.lastListenedAt, d.lastListenedAt),
                        )
                    }
                    val changed = merged.filter { (k, v) -> before[k] != v }.values.toList()
                    dao.putDays(changed)
                    days += changed.size
                }
            }
        } catch (e: Exception) {
            createdCovers.forEach { runCatching { it.delete() } }
            throw e
        }
        oldCovers.forEach { covers.delete(it) }

        // Rules go through the same repository functions the screens use (they also keep the search index right).
        var overrides = 0
        var hidden = 0
        var excluded = 0
        if (LibrarySection.RULES in selected) {
            val existingOverrides = dao.overrides().associate { it.trackUri to it.primaryArtist }
            val existingHidden = dao.hidden().mapTo(HashSet()) { it.trackUri }
            val wantedOverrides = data.overrides.mapNotNull { o -> resolved.getOrNull(o.track)?.let { it to o.primaryArtist } }
            val wantedHidden = data.hidden.mapNotNull { h -> resolved.getOrNull(h.track) }
            skipped += (data.overrides.size - wantedOverrides.size) + (data.hidden.size - wantedHidden.size)
            if (replace) {
                val keep = wantedOverrides.map { it.first }.toSet()
                existingOverrides.keys.filter { it !in keep }.forEach { library.setPrimaryArtist(it, null) }
                library.unhideAll()
                dao.deleteAllExcluded()
                existingHidden.clear()
            }
            for ((uri, artist) in wantedOverrides) {
                if (!replace && uri in existingOverrides) continue
                if (replace && existingOverrides[uri] == artist) continue
                library.setPrimaryArtist(uri, artist); overrides++
            }
            for (uri in wantedHidden.distinct()) {
                if (uri in existingHidden) continue
                library.hideTrack(uri); hidden++
            }
            val existingExcluded: Set<String> = if (replace) emptySet() else dao.excluded().mapTo(HashSet()) { it.prefix }
            for (e in data.excluded) {
                if (e.prefix in existingExcluded) continue
                library.setFolderExcluded(e.prefix, true); excluded++
            }
        }
        LibraryApplyResult(playlistsAdded, playlistsMerged, playlistTracks, favorites, history, days, overrides, hidden, excluded, skipped)
    }

    /** Copies a backed-up cover into the app's cover folder under a fresh name and points the playlist at it. */
    private suspend fun attachCover(playlistId: Long, name: String?, files: Map<String, File>, created: MutableList<File>) {
        val source = name?.let { files[it] } ?: return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return          // not a picture: no cover
            val dest = "$playlistId-${System.currentTimeMillis()}.jpg"
            val target = covers.file(dest)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            created += target
            dao.setCover(playlistId, dest)
        } catch (e: Exception) {
            // A cover that cannot be copied is not worth failing the whole import: the playlist simply has no own cover.
        }
    }
}
