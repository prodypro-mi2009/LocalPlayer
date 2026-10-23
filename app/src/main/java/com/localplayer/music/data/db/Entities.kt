package com.localplayer.music.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where MediaStore registered the file. Both kinds are played as music tracks. */
object MediaKind {
    const val AUDIO = 0
    /** MP4 files Android classified as video; only their audio track is used. */
    const val VIDEO = 1
}

/**
 * CACHE/INDEX of one audio file. The audio FILE is the source of truth for every tag below;
 * these columns are copies, rebuilt from the file on every (re)scan and after every verified
 * metadata write. Nothing here may override what the file says.
 *
 * Track identity is [uri] (a MediaStore item URI that stays the same when tags are edited).
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["title"]),
        Index(value = ["primaryArtistKey"]),
        Index(value = ["albumGroupKey"]),
        Index(value = ["genreKey"]),
        Index(value = ["year"]),
        Index(value = ["titleSort"]),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    /** One of [MediaKind]. */
    val mediaType: Int,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val title: String,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val hasArtwork: Boolean,
    /** False when playback failed because the file vanished or became unreadable. */
    val isAvailable: Boolean = true,
    /** True for MP4 files from the video collection that do not look like music. */
    val isExcluded: Boolean = false,
    val scannedAt: Long,

    // --- added in database version 4 (all derived from the tags above) ---
    /** Library "artist" this track is filed under (user's classification override, else the automatic one). */
    val primaryArtist: String? = null,
    val primaryArtistKey: String? = null,
    /** Result of the automatic primary-artist parser, kept so an override can be cleared. */
    val autoPrimaryArtist: String? = null,
    val albumKey: String? = null,
    val albumGroupKey: String? = null,
    val genreKey: String? = null,
    val titleSort: String? = null,
    val artistSort: String? = null,
    val albumSort: String? = null,
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = 0,
    /** Version of the derivation rules used for this row; a mismatch forces a re-read of the file. */
    @ColumnInfo(defaultValue = "0") val indexVersion: Int = 0,

    // --- added in database version 6 ---
    /** Folder the file is in (see FolderKeys). Null until the first scan after the upgrade fills it in. */
    val folderKey: String? = null,
)

/** App data: a user playlist. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    // --- added in database version 6: a playlist that mirrors a folder (all null/0 for a normal playlist) ---
    val sourceFolderKey: String? = null,
    val sourceFolderLabel: String? = null,
    @ColumnInfo(defaultValue = "1") val sourceIncludesSubfolders: Boolean = true,
    @ColumnInfo(defaultValue = "0") val folderSyncedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val folderSyncAdded: Int = 0,
    @ColumnInfo(defaultValue = "0") val folderSyncRemoved: Int = 0,
    /** 0 = in step with the folder, 1 = the folder looked empty/unreadable at the last sync and nothing was removed. */
    @ColumnInfo(defaultValue = "0") val folderSyncStatus: Int = 0,
    // --- added in database version 8: file name (inside filesDir/playlist_covers) of the user's own cover, or null ---
    val coverFile: String? = null,
)

/**
 * App data: playlist membership and order. Tracks are referenced by their stable URI (not by the
 * cache row id), so a playlist survives a re-index, and one track can be in a playlist only once.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["trackUri"]),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackUri: String,
    val position: Int,
    val addedAt: Long,
)

/** App data: one meaningful play of a track. */
@Entity(
    tableName = "listening_history",
    indices = [Index(value = ["playedAt"]), Index(value = ["trackUri"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackUri: String,
    val playedAt: Long,
)

/** App data: a favourite song. Referenced by the stable track URI, so it survives re-indexing and tag edits. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackUri: String,
    val addedAt: Long,
)

/**
 * App data: the user's choice of which artist a track is FILED under in the library.
 * This is a classification rule of this app, NOT file metadata: the file's Artist tag is untouched.
 */
@Entity(tableName = "primary_artist_overrides")
data class PrimaryArtistOverrideEntity(
    @PrimaryKey val trackUri: String,
    val primaryArtist: String,
)

/**
 * App data: a song the user hid from the library. Keyed by the track URI, so it survives re-scans.
 * The file is never touched. [displayName] and [sizeBytes] let a moved file be recognised again.
 */
@Entity(tableName = "hidden_tracks")
data class HiddenTrackEntity(
    @PrimaryKey val trackUri: String,
    val hiddenAt: Long,
    val displayName: String?,
    val sizeBytes: Long?,
)

/** App data: a folder (and everything below it) the user switched off in the library. [prefix] is a folder key. */
@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val prefix: String,
    val excludedAt: Long,
)

/**
 * App data: a folder the user granted access to through the system folder picker (persisted URI permission).
 * Its songs are indexed in addition to what MediaStore lists.
 */
@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey val treeUri: String,
    val folderKey: String,
    val label: String,
    val addedAt: Long,
)

/**
 * App data (database version 7): real listening, summed per LOCAL calendar day and song. [day] is yyyymmdd on the
 * device's calendar at the time of listening; [listenedMs] is real playback time; [plays] counts plays that met the
 * play criterion (ListeningMeter / PlayCriterion). Referenced by the stable track URI like favourites, so it
 * survives re-indexing and tag edits and is never deleted when a song is hidden.
 */
@Entity(
    tableName = "listening_days",
    primaryKeys = ["day", "trackUri"],
    indices = [Index(value = ["trackUri"])],
)
data class ListeningDayEntity(
    val day: Int,
    val trackUri: String,
    val listenedMs: Long,
    val plays: Int,
    val lastListenedAt: Long,
)
