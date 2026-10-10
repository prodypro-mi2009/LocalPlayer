package com.localplayer.music.data.library

/**
 * Turns tag values into the CACHE columns used by the library screens.
 * Everything derived here is rebuilt from the file whenever the file is (re)read.
 */
object TrackIndexing {
    /** Bump when the derivation rules change: every track is then re-read from its file once. */
    const val INDEX_VERSION = 1

    /** Rows created by the MediaStore-only fast scanner (tags NOT read from the file). */
    const val INDEX_VERSION_FAST = -1

    data class Derived(
        val autoPrimaryArtist: String?,
        val primaryArtist: String?,
        val primaryArtistKey: String?,
        val albumKey: String?,
        val albumGroupKey: String?,
        val genreKey: String?,
        val titleSort: String,
        val artistSort: String?,
        val albumSort: String?,
    )

    fun derive(
        title: String,
        artist: String?,
        albumArtist: String?,
        album: String?,
        genre: String?,
        knownGroupNames: Set<String>,
        primaryOverride: String?,
        structuredArtists: List<String> = emptyList(),
    ): Derived {
        val auto = ArtistParser.primaryArtist(artist, albumArtist, structuredArtists, knownGroupNames)
        val effective = primaryOverride?.takeIf { it.isNotBlank() } ?: auto
        val autoKey = TextNormalizer.groupKey(auto)
        val albumKey = TextNormalizer.groupKey(album)
        // Albums are grouped by album title + album artist (or the AUTOMATIC primary artist),
        // so a manual primary-artist override never splits an album.
        val albumOwnerKey = TextNormalizer.groupKey(albumArtist) ?: autoKey ?: ""
        return Derived(
            autoPrimaryArtist = auto,
            primaryArtist = effective,
            primaryArtistKey = TextNormalizer.groupKey(effective),
            albumKey = albumKey,
            albumGroupKey = "${albumKey ?: ""}|$albumOwnerKey",
            genreKey = TextNormalizer.groupKey(genre),
            titleSort = TextNormalizer.fold(title),
            artistSort = effective?.let { TextNormalizer.fold(it) },
            albumSort = album?.let { TextNormalizer.fold(it) },
        )
    }

    /** First 4-digit run of a year tag ("2025", "2025-03-01"), or null. */
    fun parseYear(text: String?): Int? =
        text?.let { Regex("(\\d{4})").find(it)?.value?.toIntOrNull() }

    fun parseLeadingInt(text: String?): Int? =
        text?.substringBefore('/')?.trim()?.toIntOrNull()
}
