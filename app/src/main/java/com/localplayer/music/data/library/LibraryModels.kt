package com.localplayer.music.data.library

import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.playback.ArtworkRef

enum class SongSort(val orderBy: String) {
    TITLE("titleSort, id"),
    ARTIST(
        "artistSort IS NULL, artistSort, albumSort IS NULL, albumSort, COALESCE(discNumber, 1), " +
            "trackNumber IS NULL, trackNumber, titleSort",
    ),
    ALBUM(
        "albumSort IS NULL, albumSort, COALESCE(discNumber, 1), trackNumber IS NULL, trackNumber, titleSort",
    ),
    DATE_ADDED("dateAdded DESC, id DESC"),
    YEAR("year IS NULL, year DESC, titleSort"),
    DURATION("durationMs DESC, titleSort"),
}

data class ArtistItem(val key: String, val name: String?, val trackCount: Int, val artwork: ArtworkRef?)

data class AlbumItem(
    val groupKey: String,
    val title: String?,
    val artist: String?,
    val year: Int?,
    val trackCount: Int,
    val durationMs: Long,
    val artwork: ArtworkRef?,
)

data class GenreItem(val key: String, val name: String?, val trackCount: Int)

data class YearItem(val year: Int, val trackCount: Int)

data class PlaylistItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    /** Non-null for a playlist that mirrors a folder (see FolderPlaylistSync). */
    val sourceFolderKey: String? = null,
    val sourceFolderLabel: String? = null,
    val includesSubfolders: Boolean = true,
    val folderSyncedAt: Long = 0,
    val folderSyncAdded: Int = 0,
    val folderSyncRemoved: Int = 0,
    /** True when the folder looked empty/unreadable at the last sync and nothing was removed. */
    val folderUnavailable: Boolean = false,
    /** File name of the user's own cover (see PlaylistCoverStore), or null: the 2x2 collage of the songs is shown. */
    val coverFile: String? = null,
) {
    val isFolderLinked: Boolean get() = sourceFolderKey != null
}

data class SearchResults(
    val songs: List<TrackListItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

data class AddResult(val added: Int, val alreadyThere: Int, val blockedByFolderLink: Boolean = false)

/** One folder of the library as shown in Settings. */
data class AddedFolder(
    val treeUri: String,
    val key: String,
    val label: String,
    val trackCount: Int,
    /** False when the persisted permission is gone (revoked in system settings, volume removed...). */
    val accessible: Boolean,
)

data class FolderState(val folders: List<FolderEntry>, val added: List<AddedFolder>)

sealed interface AddFolderResult {
    data class Added(val label: String) : AddFolderResult
    data object AlreadyAdded : AddFolderResult
    /** The picked folder is not on a storage volume this app can map (e.g. the Downloads provider). */
    data object Unsupported : AddFolderResult
    data object NoPermission : AddFolderResult
}

/** What the "playlist from folder" dialog shows before anything is created. */
data class FolderAnalysis(
    val treeUri: String,
    val folderKey: String,
    val label: String,
    /** Supported files found in the folder (and below) by reading it. */
    val filesOnDisk: Int,
    /** Songs of this folder already in the library. */
    val inLibrary: Int,
)
