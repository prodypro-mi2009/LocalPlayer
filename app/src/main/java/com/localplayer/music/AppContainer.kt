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
    val library: LibraryRepository by lazy {
        LibraryRepository(database, scanner, fastScanner, coordinator, settings, searchIndex, folderAccess)
    }
    val metadata: MetadataRepository by lazy { MetadataRepository(appContext, database, searchIndex) { artwork.invalidate(it) } }
    val audioSpecs: AudioSpecReader by lazy { AudioSpecReader(appContext) }
    val background: BackgroundRepository by lazy { BackgroundRepository(appContext, settings) }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }
}
