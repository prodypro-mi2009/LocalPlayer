package com.localplayer.music.ui.nav

/** Every screen of the app. Detail screens carry just enough to reload their data. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Recent : Screen
    data object Settings : Screen
    data object Queue : Screen
    /** Lyrics of the CURRENT song (follows song changes). */
    data object Lyrics : Screen
    data class LyricsEditor(val trackId: Long) : Screen
    /** [key] is the artist group key (empty = Unknown Artist); [name] is the display name. */
    data class Artist(val key: String, val name: String) : Screen
    data class Album(val groupKey: String, val title: String) : Screen
    data class Genre(val key: String, val name: String) : Screen
    /** year = 0 means "Unknown Year". */
    data class Year(val year: Int) : Screen
    data class Playlist(val id: Long) : Screen
    data class AddTracks(val playlistId: Long) : Screen
    data class EditMetadata(val trackId: Long) : Screen
}

private const val SEP = "\u0001"

fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    Screen.Recent -> "recent"
    Screen.Settings -> "settings"
    Screen.Queue -> "queue"
    Screen.Lyrics -> "lyrics"
    is Screen.LyricsEditor -> listOf("lyricseditor", trackId.toString()).joinToString(SEP)
    is Screen.Artist -> listOf("artist", key, name).joinToString(SEP)
    is Screen.Album -> listOf("album", groupKey, title).joinToString(SEP)
    is Screen.Genre -> listOf("genre", key, name).joinToString(SEP)
    is Screen.Year -> listOf("year", year.toString()).joinToString(SEP)
    is Screen.Playlist -> listOf("playlist", id.toString()).joinToString(SEP)
    is Screen.AddTracks -> listOf("addtracks", playlistId.toString()).joinToString(SEP)
    is Screen.EditMetadata -> listOf("edit", trackId.toString()).joinToString(SEP)
}

fun decodeScreen(text: String): Screen {
    val parts = text.split(SEP)
    return try {
        when (parts[0]) {
            "search" -> Screen.Search
            "recent" -> Screen.Recent
            "settings" -> Screen.Settings
            "queue" -> Screen.Queue
            "lyrics" -> Screen.Lyrics
            "lyricseditor" -> Screen.LyricsEditor(parts[1].toLong())
            "artist" -> Screen.Artist(parts[1], parts.getOrElse(2) { "" })
            "album" -> Screen.Album(parts[1], parts.getOrElse(2) { "" })
            "genre" -> Screen.Genre(parts[1], parts.getOrElse(2) { "" })
            "year" -> Screen.Year(parts[1].toInt())
            "playlist" -> Screen.Playlist(parts[1].toLong())
            "addtracks" -> Screen.AddTracks(parts[1].toLong())
            "edit" -> Screen.EditMetadata(parts[1].toLong())
            else -> Screen.Home
        }
    } catch (e: Exception) {
        Screen.Home
    }
}
