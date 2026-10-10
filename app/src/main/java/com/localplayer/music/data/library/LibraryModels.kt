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

data class PlaylistItem(val id: Long, val name: String, val trackCount: Int, val totalDurationMs: Long)

data class SearchResults(
    val songs: List<TrackListItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

data class AddResult(val added: Int, val alreadyThere: Int)
