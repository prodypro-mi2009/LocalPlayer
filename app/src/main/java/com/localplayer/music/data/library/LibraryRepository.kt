package com.localplayer.music.data.library

import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.localplayer.music.data.db.AlbumRow
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.ExcludedFolderEntity
import com.localplayer.music.data.db.FavoriteEntity
import com.localplayer.music.data.db.HiddenTrackEntity
import com.localplayer.music.data.db.LibraryFolderEntity
import com.localplayer.music.data.db.ArtistRow
import com.localplayer.music.data.db.PlaylistRow
import com.localplayer.music.data.db.PlaylistEntity
import com.localplayer.music.data.db.PlaylistTrackEntity
import com.localplayer.music.data.db.PrimaryArtistOverrideEntity
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.db.TRACK_COLS
import com.localplayer.music.data.db.TRACK_COLS_T
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.db.VISIBLE
import com.localplayer.music.data.db.VISIBLE_T
import com.localplayer.music.playback.ArtworkRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The single entry point the UI layer uses for library data.
 *
 * Everything file-related is a CACHE here: lists, groups and search are built from rows that
 * the scanner (or a verified metadata write) derived from the audio files. Playlists, history
 * and the primary-artist classification are app data and live only in Room.
 */
class LibraryRepository(
    private val database: AppDatabase,
    private val scanner: LibraryScanner,
    private val fastScanner: MediaStoreFastScanner,
    private val coordinator: ScanCoordinator,
    private val settings: AppSettings,
    private val searchIndex: SearchIndex,
    private val folderAccess: FolderAccess,
) {
    private val tracks = database.trackDao()
    private val playlists = database.playlistDao()
    private val history = database.historyDao()

    val scanState: StateFlow<ScanState> = coordinator.state

    /** Changes whenever the track cache changes; lets search re-run after scans and edits. */
    val changeStamp: Flow<Long> = tracks.observeStamp()

    /** ON in Settings = MediaStore-only fast scanner (no fallback); OFF = normal scanner. */
    suspend fun rescan() = coordinator.run {
        if (settings.useMediaStoreScanner.value) fastScanner.pass() else scanner.pass()
        // Folders granted through the system picker are indexed in addition to what MediaStore lists.
        val granted = folderDao.libraryFolders()
        if (granted.isNotEmpty()) scanner.passFolders(granted, folderAccess)
        // Only after a pass that did not fail: a failed query must not look like "all files are gone".
        if (coordinator.state.value.error == null) {
            reconcileHidden()
            syncFolderPlaylists()
        }
    }

    // --- hidden songs (app data; the file is never touched) ------------------------------------------

    private val hiddenDao = database.hiddenDao()
    private val folderDao = database.folderDao()

    fun hiddenTracks(): Flow<List<TrackListItem>> = hiddenDao.observeHiddenTracks()

    fun hiddenCount(): Flow<Int> = hiddenDao.observeHiddenCount()

    /** Removes the song from every library list, search and mix. Favourites and playlists keep their entry. */
    suspend fun hideTrack(trackUri: String) {
        val entity = tracks.getEntityByUri(trackUri)
        hiddenDao.hide(HiddenTrackEntity(trackUri, System.currentTimeMillis(), entity?.displayName, entity?.sizeBytes))
    }

    suspend fun unhideTrack(trackUri: String) = hiddenDao.unhide(trackUri)

    suspend fun unhideAll() = hiddenDao.unhideAll()

    /** Hidden entries whose file moved get the new URI; entries whose file is long gone are dropped. */
    private suspend fun reconcileHidden() {
        val orphans = hiddenDao.orphans()
        if (orphans.isEmpty()) return
        val plan = HiddenReconciler.plan(
            orphans = orphans.map { HiddenReconciler.Orphan(it.uri, it.hiddenAt, it.displayName, it.sizeBytes) },
            candidates = hiddenDao.candidates().map { HiddenReconciler.Candidate(it.uri, it.displayName, it.sizeBytes) },
            now = System.currentTimeMillis(),
        )
        if (plan.rekey.isEmpty() && plan.drop.isEmpty()) return
        database.withTransaction {
            val byUri = orphans.associateBy { it.uri }
            for ((oldUri, newUri) in plan.rekey) {
                val o = byUri[oldUri] ?: continue
                hiddenDao.hide(HiddenTrackEntity(newUri, o.hiddenAt, o.displayName, o.sizeBytes))
                hiddenDao.unhide(oldUri)
            }
            if (plan.drop.isNotEmpty()) hiddenDao.delete(plan.drop)
        }
    }

    // --- library folders -----------------------------------------------------------------------------

    fun folderState(): Flow<FolderState> =
        combine(tracks.observeFolderRows(), folderDao.observeExcluded(), folderDao.observeLibraryFolders()) { rows, excluded, added ->
            val counts = rows.associate { it.folderKey to it.trackCount }
            FolderState(
                folders = FolderOverview.build(counts, excluded.map { it.prefix }.toSet()),
                added = added.map { f ->
                    AddedFolder(
                        treeUri = f.treeUri,
                        key = f.folderKey,
                        label = f.label,
                        trackCount = rows.filter { FolderKeys.covers(f.folderKey, it.folderKey) }.sumOf { it.trackCount },
                        accessible = folderAccess.hasPermission(Uri.parse(f.treeUri)),
                    )
                },
            )
        }

    /** Switches a folder (and everything below it) off or on in the library. No file is touched. */
    suspend fun setFolderExcluded(folderKey: String, excluded: Boolean) {
        if (excluded) folderDao.exclude(ExcludedFolderEntity(folderKey, System.currentTimeMillis()))
        else folderDao.include(folderKey)
    }

    /** Takes the persistable permission of a picked folder, remembers it and indexes its songs. */
    suspend fun addLibraryFolder(treeUri: Uri): AddFolderResult {
        val key = folderAccess.folderKeyOf(treeUri) ?: return AddFolderResult.Unsupported
        val text = treeUri.toString()
        if (folderDao.libraryFolders().any { it.treeUri == text }) return AddFolderResult.AlreadyAdded
        if (!folderAccess.persist(treeUri)) return AddFolderResult.NoPermission
        val label = folderAccess.displayName(treeUri)
        folderDao.upsertLibraryFolder(LibraryFolderEntity(text, key, label, System.currentTimeMillis()))
        rescan()
        return AddFolderResult.Added(label)
    }

    /** Gives the permission back and removes the songs that came only from this folder from the library index. */
    suspend fun removeLibraryFolder(treeUri: String) {
        folderAccess.release(Uri.parse(treeUri))
        val ids = tracks.idsByUriPrefix(FolderAccess.rowPrefix(treeUri))
        database.withTransaction {
            for (chunk in ids.chunked(500)) tracks.deleteByIds(chunk)
            folderDao.deleteLibraryFolder(treeUri)
        }
        searchIndex.delete(ids)
    }

    // --- playlists that mirror a folder ----------------------------------------------------------------

    /** Reads the folder (nothing is created or stored). Null when the folder cannot be used. */
    suspend fun analyzeFolder(treeUri: Uri): FolderAnalysis? {
        val key = folderAccess.folderKeyOf(treeUri) ?: return null
        val walk = withContext(Dispatchers.IO) { folderAccess.walk(treeUri) } ?: return null
        return FolderAnalysis(
            treeUri = treeUri.toString(),
            folderKey = key,
            label = folderAccess.displayName(treeUri),
            filesOnDisk = walk.files.size,
            inLibrary = tracks.urisInFolder(key, 1).size,
        )
    }

    /**
     * Creates a playlist that REFERENCES the songs of a folder (nothing is copied or moved) and keeps it in
     * step after every scan. When the folder holds files MediaStore does not list, the folder is added to the
     * library (persisted permission) so they can be played; otherwise no permission is kept.
     */
    suspend fun createFolderPlaylist(treeUri: Uri, name: String, analysis: FolderAnalysis): Long? {
        val needsAccess = analysis.filesOnDisk > analysis.inLibrary
        if (needsAccess) {
            val key = analysis.folderKey
            val text = treeUri.toString()
            if (folderDao.libraryFolders().none { it.treeUri == text }) {
                if (!folderAccess.persist(treeUri)) return null
                folderDao.upsertLibraryFolder(LibraryFolderEntity(text, key, analysis.label, System.currentTimeMillis()))
            }
            rescan()
        }
        val now = System.currentTimeMillis()
        val id = playlists.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                sourceFolderKey = analysis.folderKey,
                sourceFolderLabel = analysis.label,
            ),
        )
        syncFolderPlaylist(playlists.getPlaylist(id) ?: return id)
        return id
    }

    /** The playlist becomes a normal one again; its songs stay. */
    suspend fun unlinkPlaylistFromFolder(id: Long) {
        playlists.linkFolder(id, null, null, true)
        playlists.setSyncResult(id, 0, 0, 0, 0)
    }

    private suspend fun syncFolderPlaylists() {
        for (p in playlists.folderPlaylists()) syncFolderPlaylist(p)
    }

    private suspend fun syncFolderPlaylist(p: PlaylistEntity) {
        val key = p.sourceFolderKey ?: return
        val desired = tracks.urisInFolder(key, if (p.sourceIncludesSubfolders) 1 else 0)
        database.withTransaction {
            val diff = FolderPlaylistSync.diff(playlists.orderedUris(p.id), desired)
            val now = System.currentTimeMillis()
            when {
                diff.folderLooksUnavailable ->
                    if (p.folderSyncStatus != 1) playlists.setSyncResult(p.id, now, 0, 0, 1)
                diff.changed -> {
                    for (uri in diff.remove) playlists.removeTrack(p.id, uri)
                    var position = playlists.maxPosition(p.id)
                    for (uri in diff.add) {
                        // The primary key (playlistId, trackUri) makes a repeated scan unable to add a file twice.
                        if (playlists.insertTrack(PlaylistTrackEntity(p.id, uri, position + 1, now)) != -1L) position++
                    }
                    renumber(p.id, playlists.orderedUris(p.id))
                    playlists.setSyncResult(p.id, now, diff.add.size, diff.remove.size, 0)
                }
                p.folderSyncStatus != 0 ->
                    playlists.setSyncResult(p.id, p.folderSyncedAt, p.folderSyncAdded, p.folderSyncRemoved, 0)
            }
        }
    }

    // --- favourites (app data) ---------------------------------------------------------------

    private val favorites = database.favoriteDao()

    fun isFavorite(trackUri: String): Flow<Boolean> = favorites.observeIsFavorite(trackUri)

    fun favoriteTracks(): Flow<List<TrackListItem>> = favorites.observeFavoriteTracks()

    /** Returns the new state. */
    suspend fun toggleFavorite(trackUri: String): Boolean = database.withTransaction {
        if (favorites.isFavorite(trackUri)) {
            favorites.delete(trackUri)
            false
        } else {
            favorites.insert(FavoriteEntity(trackUri, System.currentTimeMillis()))
            true
        }
    }

    // --- songs ---------------------------------------------------------------------------

    fun songs(sort: SongSort): Flow<List<TrackListItem>> =
        tracks.observeTracksRaw(
            SimpleSQLiteQuery("SELECT $TRACK_COLS FROM tracks WHERE $VISIBLE ORDER BY ${sort.orderBy}"),
        )

    suspend fun trackItem(trackId: Long): TrackListItem? = tracks.getItem(trackId)

    fun recentlyPlayed(): Flow<List<TrackListItem>> = tracks.observeRecentlyPlayed()

    suspend fun clearHistory() = history.clear()

    // --- groups --------------------------------------------------------------------------

    fun artists(): Flow<List<ArtistItem>> = tracks.observeArtistRows().map { rows -> mergeArtists(rows) }

    fun albums(): Flow<List<AlbumItem>> = tracks.observeAlbumRows().map { rows -> rows.map(::toAlbum).sortedAlbums() }

    fun albumsOfArtist(artistKey: String): Flow<List<AlbumItem>> =
        tracks.observeAlbumRowsForArtist(artistKey).map { rows -> rows.map(::toAlbum).sortedAlbums() }

    fun genres(): Flow<List<GenreItem>> = tracks.observeGenreRows().map { rows ->
        rows.groupBy { it.genreKey }.map { (key, group) ->
            GenreItem(key, GroupNaming.pick(group.map { it.name to it.trackCount }), group.sumOf { it.trackCount })
        }.sortedWith(compareBy({ it.key.isEmpty() }, { TextNormalizer.fold(it.name) }))
    }

    fun years(): Flow<List<YearItem>> =
        tracks.observeYearRows().map { rows -> rows.map { YearItem(it.year, it.trackCount) } }

    fun artistTracks(key: String) = tracks.observeArtistTracks(key)
    fun albumTracks(groupKey: String) = tracks.observeAlbumTracks(groupKey)
    fun genreTracks(key: String) = tracks.observeGenreTracks(key)
    fun yearTracks(year: Int) = tracks.observeYearTracks(year)

    private fun mergeArtists(rows: List<ArtistRow>): List<ArtistItem> =
        rows.groupBy { it.artistKey }.map { (key, group) ->
            val art = group.firstOrNull { it.artId != null }
            ArtistItem(
                key = key,
                name = GroupNaming.pick(group.map { it.name to it.trackCount }),
                trackCount = group.sumOf { it.trackCount },
                artwork = art?.toRef(),
            )
        }.sortedWith(compareBy({ it.key.isEmpty() }, { TextNormalizer.fold(it.name) }))

    private fun toAlbum(row: AlbumRow) = AlbumItem(
        groupKey = row.groupKey,
        title = row.title,
        artist = row.artist,
        year = row.year,
        trackCount = row.trackCount,
        durationMs = row.durationMs,
        artwork = if (row.artId != null && row.artUri != null) {
            ArtworkRef(row.artId, row.artUri, row.artModified ?: 0L)
        } else {
            null
        },
    )

    private fun List<AlbumItem>.sortedAlbums() =
        sortedWith(compareBy({ it.title == null }, { TextNormalizer.fold(it.title) }, { TextNormalizer.fold(it.artist) }))

    private fun ArtistRow.toRef(): ArtworkRef? =
        if (artId != null && artUri != null) ArtworkRef(artId, artUri, artModified ?: 0L) else null

    // --- primary artist classification (app data; the file's Artist tag is never touched) ------

    /**
     * Files the track under [name] in Artists. A blank [name] removes the override and restores
     * the automatic classification. The file itself is not modified.
     */
    suspend fun setPrimaryArtist(trackUri: String, name: String?) {
        database.withTransaction {
            val track = tracks.getEntityByUri(trackUri) ?: return@withTransaction
            val chosen = name?.trim()?.takeIf { it.isNotEmpty() }
            val effective = if (chosen != null) {
                tracks.upsertOverride(PrimaryArtistOverrideEntity(trackUri, chosen))
                chosen
            } else {
                tracks.deleteOverride(trackUri)
                track.autoPrimaryArtist
            }
            tracks.applyPrimaryArtist(
                uri = trackUri,
                name = effective,
                key = TextNormalizer.groupKey(effective),
                sort = effective?.let { TextNormalizer.fold(it) },
                now = System.currentTimeMillis(),
            )
        }
        tracks.getEntityByUri(trackUri)?.let { t ->
            searchIndex.upsert(
                listOf(SearchIndex.Row(t.id, t.title, t.artist, t.album, t.genre, t.year, t.primaryArtist)),
            )
        }
    }

    // --- search (FTS over cached tags; accent/case-folded) -----------------------------------

    suspend fun search(query: String): SearchResults {
        val tokens = TextNormalizer.searchTokens(query)
        if (tokens.isEmpty()) return SearchResults()

        val all = tokens.joinToString(" ") { "$it*" }
        val songs = tracks.queryTracksRaw(
            SimpleSQLiteQuery(
                "SELECT $TRACK_COLS_T FROM track_search s JOIN tracks t ON t.id = s.docid " +
                    "WHERE track_search MATCH ? AND $VISIBLE_T ORDER BY t.titleSort LIMIT 200",
                arrayOf(all),
            ),
        )

        val artistMatch = tokens.joinToString(" ") { "primaryname:$it*" }
        val artistRows = tracks.queryArtistsRaw(
            SimpleSQLiteQuery(
                "SELECT g.k AS artistKey, g.n AS name, g.c AS trackCount, a.id AS artId, a.uri AS artUri, " +
                    "a.lastModified AS artModified FROM (" +
                    "SELECT COALESCE(t.primaryArtistKey, '') AS k, t.primaryArtist AS n, COUNT(*) AS c, " +
                    "MAX(CASE WHEN t.hasArtwork = 1 THEN t.id END) AS artId " +
                    "FROM track_search s JOIN tracks t ON t.id = s.docid " +
                    "WHERE track_search MATCH ? AND $VISIBLE_T GROUP BY COALESCE(t.primaryArtistKey, ''), t.primaryArtist) g " +
                    "LEFT JOIN tracks a ON a.id = g.artId",
                arrayOf(artistMatch),
            ),
        )

        val albumMatch = tokens.joinToString(" ") { "album:$it*" }
        val albumRows = tracks.queryAlbumsRaw(
            SimpleSQLiteQuery(
                "SELECT g.k AS groupKey, g.t AS title, g.ar AS artist, g.y AS year, g.c AS trackCount, " +
                    "g.d AS durationMs, a.id AS artId, a.uri AS artUri, a.lastModified AS artModified FROM (" +
                    "SELECT t.albumGroupKey AS k, MIN(t.album) AS t, COALESCE(MIN(t.albumArtist), MIN(t.primaryArtist)) AS ar, " +
                    "MIN(t.year) AS y, COUNT(*) AS c, COALESCE(SUM(t.durationMs), 0) AS d, " +
                    "MAX(CASE WHEN t.hasArtwork = 1 THEN t.id END) AS artId " +
                    "FROM track_search s JOIN tracks t ON t.id = s.docid " +
                    "WHERE track_search MATCH ? AND $VISIBLE_T AND t.albumGroupKey IS NOT NULL GROUP BY t.albumGroupKey) g " +
                    "LEFT JOIN tracks a ON a.id = g.artId",
                arrayOf(albumMatch),
            ),
        )

        val foldedTokens = tokens
        val matchingPlaylists = playlists.observePlaylists().first()
            .filter { row ->
                val name = TextNormalizer.fold(row.name)
                foldedTokens.all { name.contains(it) }
            }
            .map { PlaylistItem(it.id, it.name, it.trackCount, it.totalDurationMs) }

        return SearchResults(
            songs = songs,
            artists = mergeArtists(artistRows),
            albums = albumRows.map(::toAlbum).sortedAlbums(),
            playlists = matchingPlaylists,
        )
    }

    // --- playlists (app data) -----------------------------------------------------------------

    fun playlists(): Flow<List<PlaylistItem>> =
        playlists.observePlaylists().map { rows -> rows.map { it.toItem() } }

    fun playlist(id: Long): Flow<PlaylistItem?> = playlists.observePlaylist(id).map { it?.toItem() }

    fun playlistTracks(id: Long): Flow<List<TrackListItem>> = playlists.observeTracks(id)

    fun playlistArtwork(id: Long): Flow<List<ArtworkRef>> =
        playlists.observeArtwork(id).map { rows -> rows.map { ArtworkRef(it.trackId, it.uri, it.lastModified) } }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlists.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun renamePlaylist(id: Long, name: String) =
        playlists.rename(id, name.trim(), System.currentTimeMillis())

    suspend fun deletePlaylist(id: Long) = playlists.delete(id)

    /** One track appears at most once per playlist; repeated adds are counted, not duplicated. */
    suspend fun addToPlaylist(playlistId: Long, trackUris: List<String>): AddResult {
        // A playlist that mirrors a folder is edited by changing the folder (or by unlinking it first).
        if (playlists.getPlaylist(playlistId)?.sourceFolderKey != null) return AddResult(0, 0, blockedByFolderLink = true)
        var added = 0
        var duplicates = 0
        database.withTransaction {
            var position = playlists.maxPosition(playlistId)
            val now = System.currentTimeMillis()
            for (uri in trackUris.distinct()) {
                val inserted = playlists.insertTrack(PlaylistTrackEntity(playlistId, uri, position + 1, now))
                if (inserted == -1L) {
                    duplicates++
                } else {
                    position++
                    added++
                }
            }
            if (added > 0) playlists.touch(playlistId, now)
        }
        return AddResult(added, duplicates)
    }

    /** Returns false (nothing changed) for a playlist that mirrors a folder. */
    suspend fun removeFromPlaylist(playlistId: Long, trackUri: String): Boolean = database.withTransaction {
        if (playlists.getPlaylist(playlistId)?.sourceFolderKey != null) return@withTransaction false
        run {
            playlists.removeTrack(playlistId, trackUri)
            renumber(playlistId, playlists.orderedUris(playlistId))
        }
        true
    }

    /** Moves [trackUri] to the place currently held by [targetUri] (both are tracks of the playlist). */
    suspend fun movePlaylistTrack(playlistId: Long, trackUri: String, targetUri: String) {
        database.withTransaction {
            val ordered = playlists.orderedUris(playlistId).toMutableList()
            val from = ordered.indexOf(trackUri)
            val to = ordered.indexOf(targetUri)
            if (from < 0 || to < 0 || from == to) return@withTransaction
            ordered.add(to, ordered.removeAt(from))
            renumber(playlistId, ordered)
        }
    }

    private suspend fun renumber(playlistId: Long, ordered: List<String>) {
        ordered.forEachIndexed { index, uri -> playlists.setPosition(playlistId, uri, index) }
        playlists.touch(playlistId, System.currentTimeMillis())
    }

    private fun PlaylistRow.toItem() = PlaylistItem(
        id = id,
        name = name,
        trackCount = trackCount,
        totalDurationMs = totalDurationMs,
        sourceFolderKey = sourceFolderKey,
        sourceFolderLabel = sourceFolderLabel,
        includesSubfolders = sourceIncludesSubfolders,
        folderSyncedAt = folderSyncedAt,
        folderSyncAdded = folderSyncAdded,
        folderSyncRemoved = folderSyncRemoved,
        folderUnavailable = folderSyncStatus == 1,
    )
}
