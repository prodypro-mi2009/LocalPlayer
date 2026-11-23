package com.localplayer.music.data.download

/**
 * "Update playlist": decides which songs of a playlist are already in the save folder, by their FILE NAMES (the app
 * names every download after its title). Pure Kotlin (no Android), so it is unit-tested on the JVM.
 */
object PlaylistSync {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val SPACES = Regex("\\s+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val COPY_SUFFIX = Regex("""\s\(\d{1,2}\)$""")
    private val AUDIO = setOf("mp3", "m4a", "flac", "aac", "ogg", "opus", "wav", "wma")

    /** A file name that every file system accepts: no path/control characters, no trailing dots, bounded length. */
    fun cleanName(title: String): String =
        title.replace(ILLEGAL, " ").replace(SPACES, " ").trim().trimEnd('.', ' ').take(120)

    /** Letters and digits only, lower case: "Don't Play Nice!" and "dont play nice" are the same key. */
    fun key(text: String): String = text.lowercase().replace(NOT_WORD, "")

    /** The keys of the audio files in a folder (extension removed; a copy suffix such as " (2)" counts as the same song). */
    fun existingKeys(fileNames: Collection<String>): Set<String> = buildSet {
        for (name in fileNames) {
            val dot = name.lastIndexOf('.')
            val ext = if (dot >= 0) name.substring(dot + 1).lowercase() else ""
            if (ext !in AUDIO) continue
            val base = name.substring(0, dot)
            add(key(base))
            add(key(base.replace(COPY_SUFFIX, "")))
        }
    }

    /** True when a song with this [title] was already saved in the folder whose file keys are [keys]. */
    fun isPresent(title: String, keys: Set<String>): Boolean {
        val k = key(cleanName(title))
        return k.isNotEmpty() && k in keys
    }

    /** One song of the library: its uri, its title and its artist text (all artist names the library knows, joined). */
    class LibrarySong(val uri: String, val title: String, val artist: String)

    class LibraryEntry(val artistKey: String, val uri: String)

    /** Title key -> the library songs with that title. */
    fun libraryIndex(songs: Collection<LibrarySong>): Map<String, List<LibraryEntry>> =
        songs.groupBy({ key(it.title) }, { LibraryEntry(key(it.artist), it.uri) }).filterKeys { it.isNotEmpty() }

    /**
     * The uri of the library song with this [title] by this [artist], or null. Titles alone are not enough ("Intro"
     * exists many times), so the artists must overlap: one name contains the other (so "A; B" matches "A"). When
     * either side has no artist at all, the title decides.
     */
    fun libraryMatch(title: String, artist: String, index: Map<String, List<LibraryEntry>>): String? {
        val candidates = index[key(cleanName(title))] ?: index[key(title)] ?: return null
        val wanted = key(artist)
        if (wanted.isEmpty()) return candidates.first().uri
        return candidates.firstOrNull { it.artistKey.isEmpty() || it.artistKey.contains(wanted) || wanted.contains(it.artistKey) }?.uri
    }

    fun isInLibrary(title: String, artist: String, index: Map<String, List<LibraryEntry>>): Boolean =
        libraryMatch(title, artist, index) != null

    /**
     * What is already there: the save folder's file keys and the library index. [useLibrary] says whether a library
     * match also counts as "already there" (the library is looked up anyway, to find the song for a playlist).
     */
    class Known(val folderKeys: Set<String>, val library: Map<String, List<LibraryEntry>>, val useLibrary: Boolean) {
        fun matches(title: String, artist: String): Boolean =
            isPresent(title, folderKeys) || (useLibrary && isInLibrary(title, artist, library))

        /** The library song for this title and artist (null = not in the library). */
        fun libraryUri(title: String, artist: String): String? = libraryMatch(title, artist, library)
    }
}
