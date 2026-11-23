package com.localplayer.music

import android.content.Context
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.artwork.ArtworkRepository
import com.localplayer.music.data.artwork.BackgroundRepository
import com.localplayer.music.data.audio.AudioSpecReader
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.library.FolderAccess
import com.localplayer.music.data.library.LibraryRepository
import com.localplayer.music.data.library.LibraryScanner
import com.localplayer.music.data.library.MediaStoreFastScanner
import com.localplayer.music.data.library.MetadataReader
import com.localplayer.music.data.library.ScanCoordinator
import com.localplayer.music.data.library.TrackIndexing
import com.localplayer.music.data.metadata.MetadataRepository
import com.localplayer.music.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(val appContext: Context) {
    /** Application context using the interface language chosen in Settings (the app context itself follows the system). */
    fun localizedContext(): Context = com.localplayer.music.data.AppLanguage.wrap(appContext)

    val settings: AppSettings by lazy { AppSettings(appContext) }
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val artwork: ArtworkRepository by lazy {
        ArtworkRepository(appContext).also { repository ->
            // The fast scanner cannot know whether a file has artwork. When the loader finds out that it
            // has none, the index is corrected so album/artist artwork never points at an artless track.
            repository.onDefinitiveMiss = { trackId ->
                ioScope.launch {
                    database.trackDao().clearUnverifiedArtwork(trackId, TrackIndexing.INDEX_VERSION_FAST)
                }
            }
        }
    }
    private val searchIndex: SearchIndex by lazy { SearchIndex(database) }
    private val metadataReader: MetadataReader by lazy { MetadataReader(appContext) }
    private val coordinator = ScanCoordinator()
    private val scanner: LibraryScanner by lazy {
        LibraryScanner(appContext, database.trackDao(), metadataReader, searchIndex, coordinator)
    }
    private val fastScanner: MediaStoreFastScanner by lazy {
        MediaStoreFastScanner(appContext, database.trackDao(), searchIndex, coordinator)
    }
    private val folderAccess: FolderAccess by lazy { FolderAccess(appContext) }
    val playlistCovers: com.localplayer.music.data.artwork.PlaylistCoverStore by lazy {
        com.localplayer.music.data.artwork.PlaylistCoverStore(appContext)
    }
    val library: LibraryRepository by lazy {
        LibraryRepository(database, scanner, fastScanner, coordinator, settings, searchIndex, folderAccess, playlistCovers)
    }
    val downloads: com.localplayer.music.data.download.DownloadManager by lazy {
        com.localplayer.music.data.download.DownloadManager(
            context = appContext,
            settings = settings,
            rescan = { library.rescan() },
            librarySongs = {
                library.songs(com.localplayer.music.data.library.SongSort.TITLE).first().map {
                    com.localplayer.music.data.download.PlaylistSync.LibrarySong(
                        it.uri,
                        it.title,
                        listOfNotNull(it.artist, it.primaryArtist, it.albumArtist).joinToString(" "),
                    )
                }
            },
        )
    }
    val spotifyFill: com.localplayer.music.data.spotify.SpotifyFill by lazy {
        com.localplayer.music.data.spotify.SpotifyFill(appContext, downloads)
    }
    val replayGain: com.localplayer.music.data.effects.ReplayGainRepository by lazy {
        com.localplayer.music.data.effects.ReplayGainRepository(appContext)
    }
    val metadata: MetadataRepository by lazy {
        MetadataRepository(appContext, database, searchIndex) {
            artwork.invalidate(it)
            com.localplayer.music.playback.AudioEffectsHub.onTrackChanged(it)
        }
    }
    val audioSpecs: AudioSpecReader by lazy { AudioSpecReader(appContext) }
    val settingsBackup: com.localplayer.music.data.backup.SettingsBackupManager by lazy {
        com.localplayer.music.data.backup.SettingsBackupManager(appContext, settings, background, customFont, libraryBackup)
    }
    val libraryBackup: com.localplayer.music.data.backup.LibraryBackupManager by lazy {
        com.localplayer.music.data.backup.LibraryBackupManager(appContext, database, library, playlistCovers)
    }
    val autoBackup: com.localplayer.music.data.backup.AutoBackupManager by lazy {
        com.localplayer.music.data.backup.AutoBackupManager(appContext, settings, settingsBackup)
    }
    val background: BackgroundRepository by lazy { BackgroundRepository(appContext, settings) }
    val customFont: com.localplayer.music.data.artwork.CustomFontRepository by lazy {
        com.localplayer.music.data.artwork.CustomFontRepository(appContext, settings)
    }
    val stats: com.localplayer.music.data.stats.StatsRepository by lazy {
        com.localplayer.music.data.stats.StatsRepository(database)
    }
    val mix: com.localplayer.music.data.mix.MixRepository by lazy {
        com.localplayer.music.data.mix.MixRepository(database, settings)
    }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }
    val jam: com.localplayer.music.jam.JamManager by lazy { com.localplayer.music.jam.JamManager(appContext, playerConnection) }
}
