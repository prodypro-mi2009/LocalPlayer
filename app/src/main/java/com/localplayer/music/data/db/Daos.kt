package com.localplayer.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

const val TRACK_COLS =
    "id, uri, title, artist, primaryArtist, primaryArtistKey, album, albumArtist, albumGroupKey, genre, year, " +
        "trackNumber, discNumber, durationMs, hasArtwork, lastModified"
const val TRACK_COLS_T =
    "t.id, t.uri, t.title, t.artist, t.primaryArtist, t.primaryArtistKey, t.album, t.albumArtist, " +
        "t.albumGroupKey, t.genre, t.year, t.trackNumber, t.discNumber, t.durationMs, t.hasArtwork, t.lastModified"
const val VISIBLE = "isAvailable = 1 AND isExcluded = 0"
const val VISIBLE_T = "t.isAvailable = 1 AND t.isExcluded = 0"

/** Lightweight projection used by lists and for building the playback queue. */
data class TrackListItem(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val primaryArtist: String?,
    /** Library artist group key ("" / null = Unknown Artist). Used for "Go to artist". */
    val primaryArtistKey: String?,
    val album: String?,
    val albumArtist: String?,
    /** Library album group key. Used for "Go to album". */
    val albumGroupKey: String?,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val hasArtwork: Boolean,
    val lastModified: Long,
)

/** What the scanner needs to decide whether a file is new, changed or unchanged. */
data class TrackSyncInfo(
    val id: Long,
    val uri: String,
    val mediaType: Int,
    val lastModified: Long,
    val sizeBytes: Long,
    val isAvailable: Boolean,
    val indexVersion: Int,
)

data class ArtRow(val trackId: Long, val uri: String, val lastModified: Long)

data class ArtistRow(
    val artistKey: String,
    val name: String?,
    val trackCount: Int,
    val artId: Long?,
    val artUri: String?,
    val artModified: Long?,
)

data class AlbumRow(
    val groupKey: String,
    val title: String?,
    val artist: String?,
    val year: Int?,
    val trackCount: Int,
    val durationMs: Long,
    val artId: Long?,
    val artUri: String?,
    val artModified: Long?,
)

data class GenreRow(val genreKey: String, val name: String?, val trackCount: Int)

data class YearRow(val year: Int, val trackCount: Int)

data class PlaylistRow(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val totalDurationMs: Long,
)

@Dao
interface TrackDao {
    // --- lists and search (SQL is built in the repository from fixed fragments, never from user text) ---
    @RawQuery(observedEntities = [TrackEntity::class])
    fun observeTracksRaw(query: SupportSQLiteQuery): Flow<List<TrackListItem>>

    @RawQuery
    suspend fun queryTracksRaw(query: SupportSQLiteQuery): List<TrackListItem>

    @RawQuery
    suspend fun queryArtistsRaw(query: SupportSQLiteQuery): List<ArtistRow>

    @RawQuery
    suspend fun queryAlbumsRaw(query: SupportSQLiteQuery): List<AlbumRow>

    // --- single tracks ---
    @Query("SELECT $TRACK_COLS FROM tracks WHERE id = :id")
    suspend fun getItem(id: Long): TrackListItem?

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getEntity(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun getEntityByUri(uri: String): TrackEntity?

    /**
     * The artwork loader read this file successfully and found no embedded picture. Only rows the
     * MediaStore fast scanner created (which cannot know) are corrected; rows read from the real
     * file already carry the true value.
     */
    @Query("UPDATE tracks SET hasArtwork = 0 WHERE id = :id AND hasArtwork = 1 AND indexVersion = :fastVersion")
    suspend fun clearUnverifiedArtwork(id: Long, fastVersion: Int): Int

    @Query("SELECT id AS trackId, uri, lastModified FROM tracks WHERE id IN (:ids)")
    suspend fun artRefs(ids: List<Long>): List<ArtRow>

    // --- library groups ---
    @Query(
        "SELECT g.k AS artistKey, g.n AS name, g.c AS trackCount, a.id AS artId, a.uri AS artUri, " +
            "a.lastModified AS artModified FROM (" +
            "SELECT COALESCE(primaryArtistKey, '') AS k, primaryArtist AS n, COUNT(*) AS c, " +
            "MAX(CASE WHEN hasArtwork = 1 THEN id END) AS artId " +
            "FROM tracks WHERE $VISIBLE GROUP BY COALESCE(primaryArtistKey, ''), primaryArtist) g " +
            "LEFT JOIN tracks a ON a.id = g.artId",
    )
    fun observeArtistRows(): Flow<List<ArtistRow>>

    @Query(
        "SELECT g.k AS groupKey, g.t AS title, g.ar AS artist, g.y AS year, g.c AS trackCount, g.d AS durationMs, " +
            "a.id AS artId, a.uri AS artUri, a.lastModified AS artModified FROM (" +
            "SELECT albumGroupKey AS k, MIN(album) AS t, COALESCE(MIN(albumArtist), MIN(primaryArtist)) AS ar, " +
            "MIN(year) AS y, COUNT(*) AS c, COALESCE(SUM(durationMs), 0) AS d, " +
            "MAX(CASE WHEN hasArtwork = 1 THEN id END) AS artId " +
            "FROM tracks WHERE $VISIBLE AND albumGroupKey IS NOT NULL GROUP BY albumGroupKey) g " +
            "LEFT JOIN tracks a ON a.id = g.artId",
    )
    fun observeAlbumRows(): Flow<List<AlbumRow>>

    @Query(
        "SELECT g.k AS groupKey, g.t AS title, g.ar AS artist, g.y AS year, g.c AS trackCount, g.d AS durationMs, " +
            "a.id AS artId, a.uri AS artUri, a.lastModified AS artModified FROM (" +
            "SELECT albumGroupKey AS k, MIN(album) AS t, COALESCE(MIN(albumArtist), MIN(primaryArtist)) AS ar, " +
            "MIN(year) AS y, COUNT(*) AS c, COALESCE(SUM(durationMs), 0) AS d, " +
            "MAX(CASE WHEN hasArtwork = 1 THEN id END) AS artId " +
            "FROM tracks WHERE $VISIBLE AND albumGroupKey IS NOT NULL " +
            "AND (primaryArtistKey = :artistKey OR (:artistKey = '' AND primaryArtistKey IS NULL)) " +
            "GROUP BY albumGroupKey) g LEFT JOIN tracks a ON a.id = g.artId",
    )
    fun observeAlbumRowsForArtist(artistKey: String): Flow<List<AlbumRow>>

    @Query(
        "SELECT COALESCE(genreKey, '') AS genreKey, genre AS name, COUNT(*) AS trackCount " +
            "FROM tracks WHERE $VISIBLE GROUP BY COALESCE(genreKey, ''), genre",
    )
    fun observeGenreRows(): Flow<List<GenreRow>>

    @Query(
        "SELECT COALESCE(year, 0) AS year, COUNT(*) AS trackCount FROM tracks WHERE $VISIBLE " +
            "GROUP BY COALESCE(year, 0) ORDER BY year DESC",
    )
    fun observeYearRows(): Flow<List<YearRow>>

    // --- detail lists ---
    @Query(
        "SELECT $TRACK_COLS FROM tracks WHERE $VISIBLE " +
            "AND (primaryArtistKey = :artistKey OR (:artistKey = '' AND primaryArtistKey IS NULL)) " +
            "ORDER BY albumSort IS NULL, albumSort, COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, titleSort",
    )
    fun observeArtistTracks(artistKey: String): Flow<List<TrackListItem>>

    @Query(
        "SELECT $TRACK_COLS FROM tracks WHERE $VISIBLE AND albumGroupKey = :groupKey " +
            "ORDER BY COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, titleSort",
    )
    fun observeAlbumTracks(groupKey: String): Flow<List<TrackListItem>>

    @Query(
        "SELECT $TRACK_COLS FROM tracks WHERE $VISIBLE " +
            "AND (genreKey = :genreKey OR (:genreKey = '' AND genreKey IS NULL)) ORDER BY titleSort",
    )
    fun observeGenreTracks(genreKey: String): Flow<List<TrackListItem>>

    @Query(
        "SELECT $TRACK_COLS FROM tracks WHERE $VISIBLE " +
            "AND (year = :year OR (:year = 0 AND year IS NULL)) ORDER BY titleSort",
    )
    fun observeYearTracks(year: Int): Flow<List<TrackListItem>>

    @Query(
        "SELECT $TRACK_COLS_T FROM (SELECT trackUri, MAX(playedAt) AS lastPlayed FROM listening_history " +
            "GROUP BY trackUri ORDER BY lastPlayed DESC LIMIT 200) h " +
            "JOIN tracks t ON t.uri = h.trackUri WHERE $VISIBLE_T ORDER BY h.lastPlayed DESC",
    )
    fun observeRecentlyPlayed(): Flow<List<TrackListItem>>

    @Query("SELECT COALESCE(MAX(scannedAt), 0) + COUNT(*) FROM tracks")
    fun observeStamp(): Flow<Long>

    // --- scanner ---
    @Query("SELECT id, uri, mediaType, lastModified, sizeBytes, isAvailable, indexVersion FROM tracks")
    suspend fun getSyncInfo(): List<TrackSyncInfo>

    @Query("SELECT DISTINCT albumArtist FROM tracks WHERE albumArtist IS NOT NULL")
    suspend fun distinctAlbumArtists(): List<String>

    @Query("SELECT trackUri, primaryArtist FROM primary_artist_overrides")
    suspend fun allOverrides(): List<PrimaryArtistOverrideEntity>

    /** Inserts rows with id = 0 (returns the new row id) and updates rows whose id exists (returns -1). */
    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>): List<Long>

    @Upsert
    suspend fun upsert(track: TrackEntity): Long

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE tracks SET isAvailable = 0 WHERE id = :id")
    suspend fun markUnavailable(id: Long)

    // --- primary artist classification (app data, never touches the file) ---
    @Upsert
    suspend fun upsertOverride(override: PrimaryArtistOverrideEntity)

    @Query("DELETE FROM primary_artist_overrides WHERE trackUri = :uri")
    suspend fun deleteOverride(uri: String)

    @Query(
        "UPDATE tracks SET primaryArtist = :name, primaryArtistKey = :key, artistSort = :sort, scannedAt = :now " +
            "WHERE uri = :uri",
    )
    suspend fun applyPrimaryArtist(uri: String, name: String?, key: String?, sort: String?, now: Long)
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(entry: PlaylistTrackEntity): Long

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackUri = :uri")
    suspend fun removeTrack(playlistId: Long, uri: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackUri = :uri")
    suspend fun setPosition(playlistId: Long, uri: String, position: Int)

    @Query("SELECT trackUri FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun orderedUris(playlistId: Long): List<String>

    @Query(
        "SELECT p.id AS id, p.name AS name, COUNT(t.id) AS trackCount, COALESCE(SUM(t.durationMs), 0) AS totalDurationMs " +
            "FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id " +
            "LEFT JOIN tracks t ON t.uri = pt.trackUri AND t.isAvailable = 1 AND t.isExcluded = 0 " +
            "GROUP BY p.id, p.name ORDER BY p.name COLLATE NOCASE",
    )
    fun observePlaylists(): Flow<List<PlaylistRow>>

    @Query(
        "SELECT p.id AS id, p.name AS name, COUNT(t.id) AS trackCount, COALESCE(SUM(t.durationMs), 0) AS totalDurationMs " +
            "FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id " +
            "LEFT JOIN tracks t ON t.uri = pt.trackUri AND t.isAvailable = 1 AND t.isExcluded = 0 " +
            "WHERE p.id = :id GROUP BY p.id, p.name",
    )
    fun observePlaylist(id: Long): Flow<PlaylistRow?>

    @Query(
        "SELECT $TRACK_COLS_T FROM playlist_tracks pt JOIN tracks t ON t.uri = pt.trackUri " +
            "WHERE pt.playlistId = :id AND $VISIBLE_T ORDER BY pt.position",
    )
    fun observeTracks(id: Long): Flow<List<TrackListItem>>

    @Query(
        "SELECT t.id AS trackId, t.uri AS uri, t.lastModified AS lastModified FROM playlist_tracks pt " +
            "JOIN tracks t ON t.uri = pt.trackUri WHERE pt.playlistId = :id AND t.hasArtwork = 1 " +
            "AND $VISIBLE_T ORDER BY pt.position LIMIT 4",
    )
    fun observeArtwork(id: Long): Flow<List<ArtRow>>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT MAX(playedAt) FROM listening_history WHERE trackUri = :uri")
    suspend fun lastPlayedAt(uri: String): Long?

    @Query(
        "DELETE FROM listening_history WHERE id NOT IN " +
            "(SELECT id FROM listening_history ORDER BY playedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM listening_history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackUri = :uri)")
    fun observeIsFavorite(uri: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackUri = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE trackUri = :uri")
    suspend fun delete(uri: String)

    @Query(
        "SELECT $TRACK_COLS_T FROM favorites f JOIN tracks t ON t.uri = f.trackUri " +
            "WHERE $VISIBLE_T ORDER BY f.addedAt DESC",
    )
    fun observeFavoriteTracks(): Flow<List<TrackListItem>>
}
