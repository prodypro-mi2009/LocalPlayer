package com.localplayer.music.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import android.net.Uri
import com.localplayer.music.data.AppLanguage
import com.localplayer.music.data.AppSettings
import com.localplayer.music.ui.components.LibraryTab
import com.localplayer.music.ui.components.TabOrder
import com.localplayer.music.data.ThemeMode
import com.localplayer.music.data.audio.AudioSpecFormatter
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.lyrics.LrcParser
import com.localplayer.music.data.lyrics.ParsedLyrics
import com.localplayer.music.data.metadata.MetadataException
import com.localplayer.music.playback.PlayerConnection
import com.localplayer.music.playback.SleepTimer
import com.localplayer.music.playback.SleepTimerState
import com.localplayer.music.data.library.AlbumItem
import com.localplayer.music.data.library.ArtistItem
import com.localplayer.music.data.library.AddFolderResult
import com.localplayer.music.data.library.FolderAnalysis
import com.localplayer.music.data.library.FolderState
import com.localplayer.music.data.library.GenreItem
import com.localplayer.music.data.library.PlaylistItem
import com.localplayer.music.data.library.ScanState
import com.localplayer.music.data.library.SearchResults
import com.localplayer.music.data.library.SongSort
import com.localplayer.music.data.library.YearItem
import com.localplayer.music.playback.MediaItemFactory
import com.localplayer.music.playback.PlayerState
import com.localplayer.music.playback.QueueOps
import com.localplayer.music.playback.QueueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.random.Random
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short message for the user: a string resource plus its arguments. */
data class UiMessage(@param:StringRes val res: Int, val args: List<String> = emptyList())

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val library = container.library
    private val player = container.playerConnection

    val scanState: StateFlow<ScanState> = library.scanState
    val playerState: StateFlow<PlayerState> = player.state
    val queue: StateFlow<List<QueueEntry>> = player.queue

    private fun <T> Flow<T>.asState(): StateFlow<T?> =
        map<T, T?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- library lists (null = still loading) ---
    private val _sort = MutableStateFlow(SongSort.TITLE)
    val sort: StateFlow<SongSort> = _sort.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<TrackListItem>?> = _sort.flatMapLatest { library.songs(it) }.asState()
    val artists: StateFlow<List<ArtistItem>?> = library.artists().asState()
    val albums: StateFlow<List<AlbumItem>?> = library.albums().asState()
    val genres: StateFlow<List<GenreItem>?> = library.genres().asState()
    val years: StateFlow<List<YearItem>?> = library.years().asState()
    val playlists: StateFlow<List<PlaylistItem>?> = library.playlists().asState()
    val recentlyPlayed: StateFlow<List<TrackListItem>?> = library.recentlyPlayed().asState()
    val favoriteTracks: StateFlow<List<TrackListItem>?> = library.favoriteTracks().asState()

    // --- detail flows (collected only while their screen is shown) ---
    fun artistTracks(key: String) = library.artistTracks(key)
    fun albumsOfArtist(key: String) = library.albumsOfArtist(key)
    fun albumTracks(groupKey: String) = library.albumTracks(groupKey)
    fun genreTracks(key: String) = library.genreTracks(key)
    fun yearTracks(year: Int) = library.yearTracks(year)
    fun playlist(id: Long) = library.playlist(id)
    fun playlistTracks(id: Long) = library.playlistTracks(id)
    fun playlistArtwork(id: Long) = library.playlistArtwork(id)

    // --- search: only active while the search screen collects it ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<SearchResults?> =
        combine(_searchQuery.debounce(250).distinctUntilChanged(), library.changeStamp) { query, _ -> query }
            .mapLatest { query -> if (query.isBlank()) null else library.search(query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSearchQuery(text: String) {
        _searchQuery.value = text
    }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        // Incremental pass when the app opens; unchanged files are skipped, so this is cheap.
        // Without the media permission the scanner simply does nothing.
        rescan()
        autoCheckForUpdate()
    }

    /**
     * Periodic update check: at most once a day, only when the user left it on and a repository is configured, and
     * silent when offline. It only reads the latest release; it never downloads or installs anything by itself.
     */
    private fun autoCheckForUpdate() {
        val context = container.appContext
        val settings = container.settings
        val repo = com.localplayer.music.data.update.AppInfo.repo(context)
        if (!com.localplayer.music.data.update.UpdateRules.shouldAutoCheck(
                settings.autoUpdateCheck.value, repo.isNotEmpty(), System.currentTimeMillis(), settings.lastUpdateCheckAt,
            )
        ) return
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000)        // not during app start
            val checker = com.localplayer.music.data.update.UpdateChecker(com.localplayer.music.data.update.AppInfo.userAgent(context))
            val result = checker.check(repo, com.localplayer.music.data.update.AppInfo.versionName(context))
            if (result is com.localplayer.music.data.update.UpdateCheck.Offline) return@launch   // try again next launch
            settings.lastUpdateCheckAt = System.currentTimeMillis()
            if (result is com.localplayer.music.data.update.UpdateCheck.Newer) {
                val already = settings.knownNewerVersion == result.release.tag
                settings.knownNewerVersion = result.release.tag
                if (!already) _messages.tryEmit(UiMessage(R.string.update_available_msg, listOf(result.release.tag)))
            }
        }
    }

    fun setSort(sort: SongSort) {
        _sort.value = sort
    }

    fun rescan() {
        viewModelScope.launch {
            library.rescan()
            container.artwork.trimDisk()
        }
    }

    // --- playback ------------------------------------------------------------------------

    private suspend fun toItems(list: List<TrackListItem>) = withContext(Dispatchers.Default) {
        val unknownArtist = container.localizedContext().getString(R.string.unknown_artist)
        val unknownAlbum = container.localizedContext().getString(R.string.unknown_album)
        list.map { MediaItemFactory.create(it, unknownArtist, unknownAlbum) }
    }

    /**
     * Queues the list (in its current order) and starts playing at [index]. For very large lists
     * only a window around the chosen track is queued, to keep the app light.
     */
    fun playFrom(songs: List<TrackListItem>, index: Int) {
        viewModelScope.launch {
            val start = (index - QUEUE_BEFORE).coerceAtLeast(0)
            val end = (index + QUEUE_AFTER).coerceAtMost(songs.size)
            player.playQueue(toItems(songs.subList(start, end)), index - start)
        }
    }

    /**
     * Plays the WHOLE list (no window). Shuffle policy (one rule for every way of starting a queue): the queue
     * follows the Shuffle switch. Shuffle all ([shuffle] = true) starts at a random song and turns the switch ON,
     * so the icon always matches the real order; Play all ([shuffle] = false) plays in list order if the switch
     * is off and shuffled if it is on (the switch is the single source of truth, it is never silently ignored).
     */
    fun playAll(songs: List<TrackListItem>, shuffle: Boolean) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val start = if (shuffle) Random.nextInt(songs.size) else 0
            player.playQueue(toItems(songs), start, forceShuffle = shuffle)
        }
    }

    /** Shuffle all of the songs matching [filter] (e.g. one tab's selection) without keeping the full library collected. */
    fun shuffleSongs(filter: (TrackListItem) -> Boolean) {
        viewModelScope.launch {
            val all = library.songs(SongSort.TITLE).first()
            val picked = withContext(Dispatchers.Default) { all.filter(filter) }
            playAll(picked, shuffle = true)
        }
    }

    /** Shuffle all songs of the given playlists, each song once, with a single query. */
    fun shufflePlaylists(ids: List<Long>) {
        viewModelScope.launch { playAll(library.tracksOfPlaylists(ids), shuffle = true) }
    }



    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun playQueueIndex(index: Int) = player.playQueueIndex(index)
    fun removeQueueIndex(index: Int) {
        viewModelScope.launch {
            player.removeQueueItem(index)
        }
    }
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
    fun moveQueueEntry(occurrenceId: String, to: Int) = player.moveQueueEntry(occurrenceId, to)
    suspend fun removeQueueEntry(occurrenceId: String) = player.removeQueueEntry(occurrenceId)
    /** Queue screen Clear: keeps only the current song, untouched. Null when there was nothing to clear. */
    suspend fun clearQueueExceptCurrent(): QueueOps.Cleared? = player.clearQueueExceptCurrent()
    suspend fun undoClearQueue(cleared: QueueOps.Cleared): Boolean = player.undoClearQueue(cleared)
    suspend fun removeQueueItem(index: Int) = player.removeQueueItem(index)
    fun reinsertQueueItem(removed: PlayerConnection.RemovedItem) = player.reinsert(removed)
    suspend fun clearUpcoming(): QueueOps.Cleared? = player.clearUpcoming()
    fun currentPositionMs(): Long = player.currentPositionMs()

    // --- playlists (app data) --------------------------------------------------------------

    fun createPlaylist(name: String, thenAdd: List<String> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            if (thenAdd.isNotEmpty()) {
                library.addToPlaylist(id, thenAdd)
                _messages.tryEmit(UiMessage(R.string.added_to_playlist, listOf(name.trim())))
            } else {
                _messages.tryEmit(UiMessage(R.string.playlist_created))
            }
        }
    }

    fun addToPlaylist(playlist: PlaylistItem, trackUris: List<String>) {
        viewModelScope.launch {
            val result = library.addToPlaylist(playlist.id, trackUris)
            val message = if (result.blockedByFolderLink) {
                UiMessage(R.string.playlist_is_folder_linked, listOf(playlist.name))
            } else if (result.added == 0 && result.alreadyThere > 0) {
                UiMessage(R.string.already_in_playlist, listOf(playlist.name))
            } else {
                UiMessage(R.string.added_to_playlist, listOf(playlist.name))
            }
            _messages.tryEmit(message)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.renamePlaylist(id, name) }
    }

    fun setPlaylistCover(id: Long, source: android.net.Uri) {
        viewModelScope.launch {
            val ok = library.setPlaylistCover(id, source)
            _messages.tryEmit(UiMessage(if (ok) R.string.playlist_cover_updated else R.string.playlist_cover_failed))
        }
    }

    fun removePlaylistCover(id: Long) {
        viewModelScope.launch {
            library.removePlaylistCover(id)
            _messages.tryEmit(UiMessage(R.string.playlist_cover_removed))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun removeFromPlaylist(playlistId: Long, trackUri: String) {
        viewModelScope.launch {
            if (!library.removeFromPlaylist(playlistId, trackUri)) {
                _messages.tryEmit(UiMessage(R.string.playlist_remove_blocked_folder))
            }
        }
    }

    fun movePlaylistTrack(playlistId: Long, trackUri: String, targetUri: String) {
        viewModelScope.launch { library.movePlaylistTrack(playlistId, trackUri, targetUri) }
    }

    // --- hidden songs -----------------------------------------------------------------------

    val hiddenTracks: StateFlow<List<TrackListItem>?> = library.hiddenTracks().asState()
    val hiddenCount: StateFlow<Int> =
        library.hiddenCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Hides the song from the library and takes it out of the playback queue. The file is not touched. */
    suspend fun hideTrack(trackUri: String) {
        library.hideTrack(trackUri)
        player.removeAllOfTrack(trackUri)
    }

    fun unhideTrack(trackUri: String) {
        viewModelScope.launch { library.unhideTrack(trackUri) }
    }

    fun unhideAll() {
        viewModelScope.launch { library.unhideAll() }
    }

    // --- library folders -----------------------------------------------------------------------

    val folderState: StateFlow<FolderState?> = library.folderState().asState()

    fun setFolderExcluded(folderKey: String, excluded: Boolean) {
        viewModelScope.launch { library.setFolderExcluded(folderKey, excluded) }
    }

    fun addLibraryFolder(treeUri: Uri) {
        viewModelScope.launch {
            val message = when (val result = library.addLibraryFolder(treeUri)) {
                is AddFolderResult.Added -> UiMessage(R.string.folder_added, listOf(result.label))
                AddFolderResult.AlreadyAdded -> UiMessage(R.string.folder_already_added)
                AddFolderResult.Unsupported -> UiMessage(R.string.folder_unsupported)
                AddFolderResult.NoPermission -> UiMessage(R.string.folder_no_permission)
            }
            _messages.tryEmit(message)
        }
    }

    fun removeLibraryFolder(treeUri: String) {
        viewModelScope.launch {
            library.removeLibraryFolder(treeUri)
            _messages.tryEmit(UiMessage(R.string.folder_access_removed))
        }
    }

    // --- playlist from a folder ------------------------------------------------------------------

    suspend fun analyzeFolder(treeUri: Uri): FolderAnalysis? = library.analyzeFolder(treeUri)

    fun createFolderPlaylist(treeUri: Uri, name: String, analysis: FolderAnalysis) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = library.createFolderPlaylist(treeUri, name, analysis)
            _messages.tryEmit(
                if (id == null) UiMessage(R.string.folder_no_permission)
                else UiMessage(R.string.folder_playlist_created, listOf(name.trim())),
            )
        }
    }

    fun unlinkPlaylistFromFolder(id: Long) {
        viewModelScope.launch { library.unlinkPlaylistFromFolder(id) }
    }

    // --- history / classification ----------------------------------------------------------

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    /** Library classification only; the file's Artist tag is not touched. Blank = back to automatic. */
    fun setPrimaryArtist(trackUri: String, name: String?) {
        viewModelScope.launch { library.setPrimaryArtist(trackUri, name) }
    }

    // --- favourites (app data, never written into the audio file) ----------------------------

    fun isFavorite(uri: String): Flow<Boolean> = library.isFavorite(uri)

    fun toggleFavorite(uri: String) {
        viewModelScope.launch { library.toggleFavorite(uri) }
    }

    // --- now playing extras ----------------------------------------------------------------

    val positionJumps: StateFlow<Int> = player.positionJumps
    val sleepTimerState: StateFlow<SleepTimerState> = SleepTimer.state

    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()

    fun startSleepTimer(minutes: Int) = SleepTimer.startMinutes(minutes)
    fun startSleepTimerEndOfTrack() = SleepTimer.startEndOfTrack()
    fun cancelSleepTimer() = SleepTimer.cancel()

    /** "48.0 kHz • 128 kbps • MP3" read from the actual file, or null when nothing is known. */
    suspend fun audioSpecsText(trackUri: String): String? {
        val track = container.database.trackDao().getEntityByUri(trackUri) ?: return null
        val specs = container.audioSpecs.read(trackUri, track.displayName, track.sizeBytes, track.durationMs, track.lastModified)
        return AudioSpecFormatter.format(specs)
    }

    sealed interface LyricsLoad {
        data object None : LyricsLoad
        data class Loaded(val lyrics: ParsedLyrics) : LyricsLoad
        data object Failed : LyricsLoad
    }

    // --- optional features (all OFF by default). Each setter stores the value, then applies it at once. ---------------

    fun setWidgetsEnabled(value: Boolean) {
        settings.setWidgetsEnabled(value)
        com.localplayer.music.widget.WidgetController.sync(container.appContext)
    }

    fun setReplayGainEnabled(value: Boolean) { settings.setReplayGainEnabled(value); com.localplayer.music.playback.AudioEffectsHub.refresh() }
    fun setReplayGainMode(value: com.localplayer.music.data.effects.ReplayGainMode) { settings.setReplayGainMode(value); com.localplayer.music.playback.AudioEffectsHub.refresh() }
    fun setReplayGainPreventClipping(value: Boolean) { settings.setReplayGainPreventClipping(value); com.localplayer.music.playback.AudioEffectsHub.refresh() }
    fun setEqualizerEnabled(value: Boolean) { settings.setEqualizerEnabled(value); com.localplayer.music.playback.AudioEffectsHub.refresh() }
    fun setEqPreset(value: com.localplayer.music.data.effects.EqPreset) { settings.setEqPreset(value); com.localplayer.music.playback.AudioEffectsHub.refresh() }
    fun setEqCustomBand(index: Int, db: Float) {
        val g = settings.eqCustom.value.copyOf()
        g[index] = db
        settings.setEqCustom(g)
        settings.setEqPreset(com.localplayer.music.data.effects.EqPreset.CUSTOM)
        com.localplayer.music.playback.AudioEffectsHub.refresh()
    }
    fun resetEqualizer() {
        settings.setEqCustom(com.localplayer.music.data.effects.EqModel.flat())
        settings.setEqPreset(com.localplayer.music.data.effects.EqPreset.FLAT)
        com.localplayer.music.playback.AudioEffectsHub.refresh()
    }
    /** After a settings import: apply restored values to the running player and widgets. */
    fun reapplyOptionalFeatures() {
        com.localplayer.music.playback.AudioEffectsHub.refresh()
        com.localplayer.music.widget.WidgetController.sync(container.appContext)
    }

    /** Bumped whenever lyrics were written into a file, so an open Lyrics page reloads them from the file. */
    private val _lyricsRevision = MutableStateFlow(0)
    val lyricsRevision: StateFlow<Int> = _lyricsRevision.asStateFlow()
    fun notifyLyricsChanged() { _lyricsRevision.value += 1 }

    /** Lyrics are read from the audio FILE every time (the file is the source of truth). */
    suspend fun loadLyrics(trackId: Long): LyricsLoad = try {
        val raw = container.metadata.load(trackId).metadata.lyrics
        val parsed = LrcParser.parse(raw)
        if (parsed.isEmpty) LyricsLoad.None else LyricsLoad.Loaded(parsed)
    } catch (e: MetadataException) {
        LyricsLoad.Failed
    }

    suspend fun trackItem(trackId: Long): TrackListItem? = library.trackItem(trackId)

    fun playTrack(trackId: Long) {
        viewModelScope.launch {
            val track = library.trackItem(trackId) ?: return@launch
            player.playQueue(toItems(listOf(track)), 0)
        }
    }

    // --- queue snapshot / undo -------------------------------------------------------------

    /** Appends the track to the END of the queue as its own occurrence; returns the id for a targeted Undo. */
    suspend fun appendToQueueEnd(track: TrackListItem): QueueOps.Added = player.appendOccurrence(toItems(listOf(track)).first())

    /** Swipe right: inserts the track as the next song (one new occurrence); returns its id for a targeted Undo. */
    suspend fun insertNextInQueue(track: TrackListItem): QueueOps.Added = player.insertNextOccurrence(toItems(listOf(track)).first())

    /** Undo of [appendToQueueEnd] / [insertNextInQueue]: removes exactly that occurrence; the current track is not touched. */
    suspend fun undoAppend(added: QueueOps.Added): Boolean = player.removeOccurrence(added)

    /** Mini-player dismiss: clears the queue; the returned snapshot (a token for exactly that state) allows one Undo. */
    suspend fun dismissQueue(): PlayerConnection.QueueSnapshot? = player.dismissQueue()

    /** Undo of [dismissQueue]; false (nothing changed) when the queue was touched after the dismissal. */
    suspend fun restoreQueue(snapshot: PlayerConnection.QueueSnapshot): Boolean = player.restoreQueue(snapshot)

    // --- settings --------------------------------------------------------------------------

    val settings: AppSettings = container.settings

    fun setUseMediaStoreScanner(enabled: Boolean) {
        settings.setUseMediaStoreScanner(enabled)
        rescan()
    }

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setArtworkColors(enabled: Boolean) = settings.setArtworkColors(enabled)

    fun setBackground(uri: Uri) {
        viewModelScope.launch {
            if (!container.background.set(uri)) _messages.tryEmit(UiMessage(R.string.background_failed))
        }
    }

    fun setLanguage(language: AppLanguage) = settings.setLanguage(language)
    fun setTabOrder(order: List<LibraryTab>) = settings.setTabOrder(TabOrder.serialize(order))
    fun resetTabOrder() = settings.setTabOrder(null)

    fun setControlColor(group: com.localplayer.music.data.personalization.ControlColorGroup, argb: Int?) =
        settings.setControlColors(settings.controlColors.value.with(group, argb))
    fun resetControlColors() = settings.setControlColors(com.localplayer.music.data.personalization.ControlColors.None)

    fun setFont(category: com.localplayer.music.data.personalization.FontCategory, choice: com.localplayer.music.data.personalization.FontChoice) =
        settings.setFontChoices(settings.fontChoices.value.with(category, choice))
    fun resetFonts() = settings.setFontChoices(com.localplayer.music.data.personalization.FontChoices.Default)

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            if (!container.customFont.import(uri)) _messages.tryEmit(UiMessage(R.string.font_import_failed))
        }
    }

    fun removeCustomFont() {
        container.customFont.remove()
        // Categories that used the imported font go back to the default font.
        var choices = settings.fontChoices.value
        for (c in com.localplayer.music.data.personalization.FontCategory.entries) {
            if (choices[c] == com.localplayer.music.data.personalization.FontChoice.CUSTOM) {
                choices = choices.with(c, com.localplayer.music.data.personalization.FontChoice.DEFAULT)
            }
        }
        settings.setFontChoices(choices)
    }

    fun setPureMode(mode: com.localplayer.music.data.personalization.PureMode) = settings.setPureMode(mode)
    fun setLyricsAlignment(value: com.localplayer.music.data.personalization.LyricsAlignment) = settings.setLyricsAlignment(value)
    fun setYourMixCardDismissed(value: Boolean) = settings.setYourMixCardDismissed(value)

    // --- listening statistics ---
    private val stats = container.stats

    fun listenedMs(range: com.localplayer.music.data.stats.DayRange): Flow<Long> = stats.listenedMs(range)
    fun playsIn(range: com.localplayer.music.data.stats.DayRange): Flow<Int> = stats.plays(range)
    fun top20(range: com.localplayer.music.data.stats.DayRange): Flow<List<com.localplayer.music.data.db.TopTrackRow>> = stats.top20(range)

    // --- Your Mix ---
    /** [loading] is true until the first Mix is ready; [tracks] is never null so the screen can always draw. */
    data class MixUi(
        val loading: Boolean = true,
        val tracks: List<TrackListItem> = emptyList(),
        val notice: com.localplayer.music.data.mix.MixNotice = com.localplayer.music.data.mix.MixNotice.NONE,
        val generatedAt: Long = 0L,
    )

    private val _mix = MutableStateFlow(MixUi())
    val mix: StateFlow<MixUi> = _mix.asStateFlow()
    private var mixJob: kotlinx.coroutines.Job? = null

    /** Loads the saved Mix or rebuilds it when it is out of date ([force] = the refresh button). One job at a time. */
    fun loadMix(force: Boolean = false) {
        mixJob?.cancel()
        mixJob = viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { container.mix.load(force) }
            _mix.value = MixUi(false, out.tracks, out.notice, out.generatedAt)
        }
    }

    fun setMixSettings(value: com.localplayer.music.data.mix.MixSettings) = settings.setMixSettings(value)

    fun setBackgroundDim(value: Float) = settings.setBackgroundDim(value)

    fun resetBackground() = container.background.reset()

    private companion object {
        const val QUEUE_BEFORE = 500
        const val QUEUE_AFTER = 1500
    }
}
