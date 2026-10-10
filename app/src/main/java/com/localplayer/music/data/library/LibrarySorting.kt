package com.localplayer.music.data.library

enum class AlbumSort { TITLE, ARTIST, YEAR, SONG_COUNT }

/** Sort orders shared by artists, playlists and genres. */
enum class NameCountSort { NAME, SONG_COUNT }

/** Pure sorting helpers for the library tabs. Names are compared folded (case/accent-insensitive, Greek included). */
object LibrarySorting {
    private fun key(text: String?) = TextNormalizer.fold(text)

    fun albums(list: List<AlbumItem>, sort: AlbumSort): List<AlbumItem> = when (sort) {
        AlbumSort.TITLE -> list.sortedWith(compareBy<AlbumItem>({ it.title == null }, { key(it.title) }))
        AlbumSort.ARTIST -> list.sortedWith(
            compareBy<AlbumItem>({ it.artist == null }, { key(it.artist) }, { key(it.title) }),
        )
        AlbumSort.YEAR -> list.sortedWith(
            compareBy<AlbumItem> { it.year == null }.thenByDescending { it.year ?: 0 }.thenBy { key(it.title) },
        )
        AlbumSort.SONG_COUNT -> list.sortedWith(
            compareByDescending<AlbumItem> { it.trackCount }.thenBy { key(it.title) },
        )
    }

    fun artists(list: List<ArtistItem>, sort: NameCountSort): List<ArtistItem> = when (sort) {
        NameCountSort.NAME -> list.sortedWith(compareBy<ArtistItem>({ it.name == null }, { key(it.name) }))
        NameCountSort.SONG_COUNT -> list.sortedWith(
            compareByDescending<ArtistItem> { it.trackCount }.thenBy { key(it.name) },
        )
    }

    fun genres(list: List<GenreItem>, sort: NameCountSort): List<GenreItem> = when (sort) {
        NameCountSort.NAME -> list.sortedWith(compareBy<GenreItem>({ it.name == null }, { key(it.name) }))
        NameCountSort.SONG_COUNT -> list.sortedWith(
            compareByDescending<GenreItem> { it.trackCount }.thenBy { key(it.name) },
        )
    }

    fun playlists(list: List<PlaylistItem>, sort: NameCountSort): List<PlaylistItem> = when (sort) {
        NameCountSort.NAME -> list.sortedBy { key(it.name) }
        NameCountSort.SONG_COUNT -> list.sortedWith(
            compareByDescending<PlaylistItem> { it.trackCount }.thenBy { key(it.name) },
        )
    }
}

/** Letters for the fast scrollbar: Latin A-Z, Greek Α-Ω and "#" for everything else. */
object AlphabetIndex {
    private const val GREEK = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ"
    const val OTHER = '#'

    /** Every possible section, in display order. */
    val all: List<Char> = ('A'..'Z').toList() + GREEK.toList() + OTHER

    /** Section of a title/name: first letter, accents and case ignored. */
    fun sectionOf(text: String?): Char {
        val folded = TextNormalizer.fold(text)
        if (folded.isEmpty()) return OTHER
        val c = folded[0]
        return when {
            c in 'a'..'z' -> c.uppercaseChar()
            c in 'α'..'ω' -> c.uppercaseChar().takeIf { it in GREEK } ?: OTHER
            else -> OTHER
        }
    }

    /** First list position of every section that occurs, in section display order. */
    fun firstIndexBySection(texts: List<String?>): LinkedHashMap<Char, Int> {
        val first = HashMap<Char, Int>()
        texts.forEachIndexed { index, text -> first.putIfAbsent(sectionOf(text), index) }
        val ordered = LinkedHashMap<Char, Int>()
        for (letter in all) first[letter]?.let { ordered[letter] = it }
        return ordered
    }
}
