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
    /** Read-only details of one song (tags, file, technical values). */
    data class TrackInfo(val trackId: Long) : Screen
    /** Songs the user hid from the library, with "show again". */
    data object HiddenSongs : Screen
    /** Songs that look like copies of each other; the extra copies can be hidden. */
    data object Duplicates : Screen
    /** Same artist / album / genre / year for every song of one album. */
    data class BulkEditAlbum(val groupKey: String, val title: String) : Screen
    /** Measure and write ReplayGain for every song of one album. */
    data class GainAlbum(val groupKey: String, val title: String) : Screen
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
    /** Settings > Playlist from Spotify: read the song names of a playlist link and compare them with the library. */
    data object SettingsSpotify : Screen
    /** Settings > Jam: listen together over the local network (host or guest). */
    data object SettingsJam : Screen
    /** Sub-pages of the settings hubs (Appearance, Library, Navigation, Audio, Your Mix). */
    data object SettingsNavigation : Screen
    data object SettingsTabOrder : Screen
    data object SettingsGestures : Screen
    data object SettingsBackButton : Screen
    data object SettingsActionRow : Screen
    data object SettingsScan : Screen
    data object SettingsTheme : Screen
    data object SettingsEqualizer : Screen
    data object SettingsPlayback : Screen
    data object SettingsReplayGain : Screen
    /** Find ReplayGain for the whole library (Settings > ReplayGain). */
    data object GainLibrary : Screen
    /** Find ReplayGain peak tags for the whole library (Settings > ReplayGain). */
    data object PeakLibrary : Screen
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
    is Screen.TrackInfo -> listOf("info", trackId.toString()).joinToString(SEP)
    Screen.HiddenSongs -> "hidden"
    Screen.Duplicates -> "duplicates"
    is Screen.BulkEditAlbum -> listOf("bulkalbum", groupKey, title).joinToString(SEP)
    is Screen.GainAlbum -> listOf("gainalbum", groupKey, title).joinToString(SEP)
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
    Screen.SettingsJam -> "set-jam"
    Screen.SettingsSpotify -> "set-spotify"
    Screen.SettingsNavigation -> "set-navigation"
    Screen.SettingsTabOrder -> "set-taborder"
    Screen.SettingsGestures -> "set-gestures"
    Screen.SettingsBackButton -> "set-backbutton"
    Screen.SettingsActionRow -> "set-actionrow"
    Screen.SettingsScan -> "set-scan"
    Screen.SettingsTheme -> "set-theme"
    Screen.SettingsEqualizer -> "set-eq"
    Screen.SettingsPlayback -> "set-playback"
    Screen.SettingsReplayGain -> "set-rg"
    Screen.GainLibrary -> "gain-lib"
    Screen.PeakLibrary -> "peak-lib"
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
            "info" -> Screen.TrackInfo(parts[1].toLong())
            "hidden" -> Screen.HiddenSongs
            "duplicates" -> Screen.Duplicates
            "bulkalbum" -> Screen.BulkEditAlbum(parts[1], parts.getOrElse(2) { "" })
            "gainalbum" -> Screen.GainAlbum(parts[1], parts.getOrElse(2) { "" })
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
            "set-jam" -> Screen.SettingsJam
            "set-spotify" -> Screen.SettingsSpotify
            "set-navigation" -> Screen.SettingsNavigation
            "set-taborder" -> Screen.SettingsTabOrder
            "set-gestures" -> Screen.SettingsGestures
            "set-backbutton" -> Screen.SettingsBackButton
            "set-actionrow" -> Screen.SettingsActionRow
            "set-scan" -> Screen.SettingsScan
            "set-theme" -> Screen.SettingsTheme
            "set-eq" -> Screen.SettingsEqualizer
            "set-playback" -> Screen.SettingsPlayback
            "set-rg" -> Screen.SettingsReplayGain
            "gain-lib" -> Screen.GainLibrary
            "peak-lib" -> Screen.PeakLibrary
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
