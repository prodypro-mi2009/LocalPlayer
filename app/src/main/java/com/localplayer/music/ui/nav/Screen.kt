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
    /** Internet (LRCLIB) lyrics search for one song; opened only by the user's button. */
    data class LyricsSearch(val trackId: Long) : Screen
    /** [key] is the artist group key (empty = Unknown Artist); [name] is the display name. */
    data class Artist(val key: String, val name: String) : Screen
    data class Album(val groupKey: String, val title: String) : Screen
    data class Genre(val key: String, val name: String) : Screen
    /** year = 0 means "Unknown Year". */
    data class Year(val year: Int) : Screen
    data class Playlist(val id: Long) : Screen
    data class AddTracks(val playlistId: Long) : Screen
    data class EditMetadata(val trackId: Long) : Screen
    data class EditCover(val trackId: Long) : Screen
    /** Songs the user hid from the library, with "show again". */
    data object HiddenSongs : Screen
    /** Library folders: granted folders, folders switched off, rescan. */
    data object LibraryFolders : Screen
    /** Appearance > Colors (dynamic colors, background, button colors). */
    data object Colors : Screen
    /** Appearance > Fonts (one font per text category). */
    data object Fonts : Screen
    /** The sub-screens of the Settings menu (one card each). */
    data object SettingsAppearance : Screen
    data object SettingsLanguage : Screen
    data object SettingsLibrary : Screen
    data object SettingsLyrics : Screen
    data object SettingsYourMix : Screen
    data object SettingsAbout : Screen
    data object SettingsBackup : Screen
    data object SettingsAudio : Screen
    data object SettingsWidgets : Screen
    /** Settings > Download from YouTube (yt-dlp, MP3). */
    data object SettingsDownload : Screen
    /** Sub-pages of the settings hubs (Appearance, Library, Navigation, Audio, Your Mix). */
    data object SettingsNavigation : Screen
    data object SettingsTabOrder : Screen
    data object SettingsGestures : Screen
    data object SettingsScan : Screen
    data object SettingsTheme : Screen
    data object SettingsEqualizer : Screen
    data object SettingsReplayGain : Screen
    data object SettingsMixGeneral : Screen
    data object SettingsMixStrategy : Screen
    data object SettingsMixOptions : Screen
    /** Your Mix: the generated list. */
    data object YourMix : Screen
    /** Listening time per period (today, week, month, year, previous month). */
    data object TimeUsage : Screen
    /** The 20 most played songs of a period. */
    data object Top20 : Screen
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
    is Screen.LyricsSearch -> listOf("lyricssearch", trackId.toString()).joinToString(SEP)
    is Screen.Artist -> listOf("artist", key, name).joinToString(SEP)
    is Screen.Album -> listOf("album", groupKey, title).joinToString(SEP)
    is Screen.Genre -> listOf("genre", key, name).joinToString(SEP)
    is Screen.Year -> listOf("year", year.toString()).joinToString(SEP)
    is Screen.Playlist -> listOf("playlist", id.toString()).joinToString(SEP)
    is Screen.AddTracks -> listOf("addtracks", playlistId.toString()).joinToString(SEP)
    is Screen.EditMetadata -> listOf("edit", trackId.toString()).joinToString(SEP)
    is Screen.EditCover -> listOf("editcover", trackId.toString()).joinToString(SEP)
    Screen.HiddenSongs -> "hidden"
    Screen.LibraryFolders -> "folders"
    Screen.Colors -> "colors"
    Screen.Fonts -> "fonts"
    Screen.SettingsAppearance -> "set-appearance"
    Screen.SettingsLanguage -> "set-language"
    Screen.SettingsLibrary -> "set-library"
    Screen.SettingsLyrics -> "set-lyrics"
    Screen.SettingsYourMix -> "set-yourmix"
    Screen.SettingsAbout -> "set-about"
    Screen.SettingsBackup -> "set-backup"
    Screen.SettingsAudio -> "set-audio"
    Screen.SettingsWidgets -> "set-widgets"
    Screen.SettingsDownload -> "set-download"
    Screen.SettingsNavigation -> "set-navigation"
    Screen.SettingsTabOrder -> "set-taborder"
    Screen.SettingsGestures -> "set-gestures"
    Screen.SettingsScan -> "set-scan"
    Screen.SettingsTheme -> "set-theme"
    Screen.SettingsEqualizer -> "set-eq"
    Screen.SettingsReplayGain -> "set-rg"
    Screen.SettingsMixGeneral -> "set-mixgeneral"
    Screen.SettingsMixStrategy -> "set-mixstrategy"
    Screen.SettingsMixOptions -> "set-mixoptions"
    Screen.YourMix -> "yourmix"
    Screen.TimeUsage -> "timeusage"
    Screen.Top20 -> "top20"
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
            "lyricssearch" -> Screen.LyricsSearch(parts[1].toLong())
            "artist" -> Screen.Artist(parts[1], parts.getOrElse(2) { "" })
            "album" -> Screen.Album(parts[1], parts.getOrElse(2) { "" })
            "genre" -> Screen.Genre(parts[1], parts.getOrElse(2) { "" })
            "year" -> Screen.Year(parts[1].toInt())
            "playlist" -> Screen.Playlist(parts[1].toLong())
            "addtracks" -> Screen.AddTracks(parts[1].toLong())
            "edit" -> Screen.EditMetadata(parts[1].toLong())
            "editcover" -> Screen.EditCover(parts[1].toLong())
            "hidden" -> Screen.HiddenSongs
            "folders" -> Screen.LibraryFolders
            "colors" -> Screen.Colors
            "fonts" -> Screen.Fonts
            "set-appearance" -> Screen.SettingsAppearance
            "set-language" -> Screen.SettingsLanguage
            "set-library" -> Screen.SettingsLibrary
            "set-lyrics" -> Screen.SettingsLyrics
            "set-yourmix" -> Screen.SettingsYourMix
            "set-about" -> Screen.SettingsAbout
            "set-backup" -> Screen.SettingsBackup
            "set-audio" -> Screen.SettingsAudio
            "set-widgets" -> Screen.SettingsWidgets
            "set-download" -> Screen.SettingsDownload
            "set-navigation" -> Screen.SettingsNavigation
            "set-taborder" -> Screen.SettingsTabOrder
            "set-gestures" -> Screen.SettingsGestures
            "set-scan" -> Screen.SettingsScan
            "set-theme" -> Screen.SettingsTheme
            "set-eq" -> Screen.SettingsEqualizer
            "set-rg" -> Screen.SettingsReplayGain
            "set-mixgeneral" -> Screen.SettingsMixGeneral
            "set-mixstrategy" -> Screen.SettingsMixStrategy
            "set-mixoptions" -> Screen.SettingsMixOptions
            "yourmix" -> Screen.YourMix
            "timeusage" -> Screen.TimeUsage
            "top20" -> Screen.Top20
            else -> Screen.Home
        }
    } catch (e: Exception) {
        Screen.Home
    }
}
