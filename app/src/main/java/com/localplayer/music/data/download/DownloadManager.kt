package com.localplayer.music.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.localplayer.music.R
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.metadata.AudioContainer
import com.localplayer.music.data.effects.Ebur128
import com.localplayer.music.data.effects.LoudnessAnalyzer
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.data.metadata.FileTagEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ItemStatus { WAITING, RUNNING, DONE, FAILED }

data class DownloadItem(
    val id: String,
    val title: String,
    val artist: String,
    /** Written into the file when not blank; when blank, the album YouTube states for the song (if any) is used. */
    val album: String = "",
    /** 4-digit year; when blank, the release year YouTube states (if any) is used. */
    val year: String = "",
    val genre: String = "",
    /** Digits only (position on the album). */
    val trackNumber: String = "",
    /** Typed by the user; blank = decided by the "same as artist" switch. */
    val albumArtist: String = "",
    /** Ticked by the user: the "fill in for many songs" action then touches only the ticked songs. */
    val selected: Boolean = false,
    /** "Update playlist": a song with this title is already in the save folder. */
    val inFolder: Boolean = false,
    /** The user asked to download it although it is already in the folder. */
    val downloadAnyway: Boolean = false,
    /** The library song that is already this one (uri), when "Update playlist" found it; used to add it to a playlist. */
    val libraryUri: String? = null,
    val status: ItemStatus = ItemStatus.WAITING,
    val progress: Float = 0f,
    val error: String? = null,
    /** A saved song whose tags could not be written completely (the song itself is saved). */
    val note: String? = null,
) {
    /** Left out of the download because it is already in the folder. */
    val skipped: Boolean get() = inFolder && !downloadAnyway
}

enum class DownloadPhase { IDLE, CHECKING, READY, DOWNLOADING }

data class DownloadState(
    val phase: DownloadPhase = DownloadPhase.IDLE,
    val items: List<DownloadItem> = emptyList(),
    /** A string resource to show as an error / note (null = nothing). */
    val messageRes: Int? = null,
    /** The technical reason behind [messageRes] (yt-dlp's own error text), selectable on screen. */
    val detail: String? = null,
    val updatingEngine: Boolean = false,
    /** yt-dlp release installed by an update; null = the version bundled with the app. */
    val engineVersion: String? = null,
    /** "nightly" or "stable" (null = the version bundled with the app). */
    val engineChannel: String? = null,
    /** Write each song's artist also as the "album artist" tag. */
    val albumArtistSame: Boolean = true,
    /** Measure each song's loudness and write the ReplayGain track tag into it (a few seconds per song). */
    val findReplayGain: Boolean = true,
    /** After all downloads: songs with the same album also get the ReplayGain album tag (they are saved at the end). */
    val findAlbumGain: Boolean = true,
    /** "Update playlist": songs whose file is already in the save folder are not downloaded again. */
    val updatePlaylist: Boolean = false,
    /** With "Update playlist": a song that is anywhere in the library (same title and artist) is skipped too. */
    val checkLibrary: Boolean = true,
    /** "When YouTube asks you to sign in" helpers (all off until the user turns them on). */
    val ytNightly: Boolean = false,
    val ytFallback: Boolean = false,
    val ytCookies: Boolean = false,
    val ytHasCookies: Boolean = false,
)

/**
 * App-scoped (it outlives the screen): edits to the list, the sequential download queue, tagging, saving and the final
 * library rescan. A started download is kept alive in the background by [DownloadService].
 */
class DownloadManager(
    private val context: Context,
    private val settings: AppSettings,
    private val rescan: suspend () -> Unit,
    /** Every song of the library (for "Update playlist" with the library check on). */
    private val librarySongs: suspend () -> List<PlaylistSync.LibrarySong> = { emptyList() },
) {
    // Nothing launched here may ever take the app down: anything unexpected becomes a message on the screen.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
            _state.update {
                it.copy(
                    phase = if (it.phase == DownloadPhase.CHECKING || it.phase == DownloadPhase.DOWNLOADING) DownloadPhase.READY else it.phase,
                    updatingEngine = false,
                    messageRes = R.string.dl_error_engine,
                    detail = YtDlpEngine.describe(t),
                )
            }
        },
    )
    private val _state = MutableStateFlow(freshState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()
    private var job: Job? = null
    private val sink = DownloadSink(context)

    /**
     * An empty screen. "Update playlist" starts off and "also look in the whole library" on; nothing is saved, so every
     * new start of the app begins like this.
     */
    private fun freshState() = DownloadState()

    // --- editing ---------------------------------------------------------------------------------------------------

    fun setTitle(id: String, title: String) = edit(id) { it.copy(title = title) }

    fun setArtist(id: String, artist: String) = edit(id) { it.copy(artist = artist) }

    fun setAlbumArtist(id: String, value: String) = edit(id) { it.copy(albumArtist = value) }

    fun setTrackNumber(id: String, value: String) = edit(id) { it.copy(trackNumber = value.filter(Char::isDigit).take(4)) }

    fun setSelected(id: String, on: Boolean) = edit(id) { it.copy(selected = on) }

    fun selectAll(on: Boolean) = _state.update { s ->
        s.copy(items = s.items.map { if (it.status == ItemStatus.DONE) it else it.copy(selected = on) })
    }

    fun setAlbumArtistSame(on: Boolean) = _state.update { it.copy(albumArtistSame = on) }

    fun setFindReplayGain(on: Boolean) = _state.update { it.copy(findReplayGain = on) }

    fun setFindAlbumGain(on: Boolean) = _state.update { it.copy(findAlbumGain = on) }

    /** Switches "Update playlist" and (re)checks the songs already listed against the save folder. */
    fun setUpdatePlaylist(on: Boolean) {
        val phase = _state.value.phase
        if (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.CHECKING) return
        // Turning Update playlist on switches the library check off, so the switch that appears below asks again.
        _state.update { it.copy(updatePlaylist = on, checkLibrary = !on) }
        recheckKnown()
    }

    fun setCheckLibrary(on: Boolean) {
        val phase = _state.value.phase
        if (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.CHECKING) return
        _state.update { it.copy(checkLibrary = on) }
        recheckKnown()
    }

    private fun recheckKnown() {
        scope.launch {
            val known = scanKnown()
            _state.update { s ->
                s.copy(
                    items = s.items.map {
                        val found = known != null && it.status != ItemStatus.DONE && known.matches(it.title, it.artist)
                        it.copy(
                            inFolder = found,
                            downloadAnyway = false,
                            libraryUri = if (found) known.libraryUri(it.title, it.artist) else null,
                        )
                    },
                )
            }
        }
    }

    fun setDownloadAnyway(id: String, on: Boolean) = edit(id) { it.copy(downloadAnyway = on) }

    /** What is already there (save folder, and the library when asked); null when "Update playlist" is off. */
    private suspend fun scanKnown(): PlaylistSync.Known? {
        val s = _state.value
        if (!s.updatePlaylist) return null
        val folder = PlaylistSync.existingKeys(sink.existingNames(settings.downloadFolder.value))
        // The library is read even when it does not decide what is skipped: it finds the songs that can go to a playlist.
        val library = PlaylistSync.libraryIndex(runCatching { librarySongs() }.getOrDefault(emptyList()))
        return PlaylistSync.Known(folder, library, s.checkLibrary)
    }

    /**
     * Fills in the given fields (blank = leave that field alone) for the ticked songs, or for every song that is not
     * saved yet when nothing is ticked. Fields that are not given keep whatever each song already has.
     */
    fun fillIn(artist: String, album: String, year: String, genre: String) = _state.update { s ->
        val anyTicked = s.items.any { it.selected && it.status != ItemStatus.DONE }
        s.copy(
            items = s.items.map { item ->
                if (item.status == ItemStatus.DONE || (anyTicked && !item.selected)) {
                    item
                } else {
                    item.copy(
                        artist = artist.trim().ifEmpty { item.artist },
                        album = album.trim().ifEmpty { item.album },
                        year = year.trim().ifEmpty { item.year },
                        genre = genre.trim().ifEmpty { item.genre },
                    )
                }
            },
        )
    }

    fun setAlbum(id: String, value: String) = edit(id) { it.copy(album = value) }

    fun remove(id: String) = _state.update { s ->
        if (s.phase == DownloadPhase.DOWNLOADING) s else s.copy(items = s.items.filterNot { it.id == id })
    }

    fun reset() {
        if (_state.value.phase == DownloadPhase.DOWNLOADING) return
        _state.value = freshState()
    }

    fun dismissMessage() = _state.update { it.copy(messageRes = null, detail = null) }

    private fun edit(id: String, change: (DownloadItem) -> DownloadItem) = _state.update { s ->
        s.copy(items = s.items.map { if (it.id == id) change(it) else it })
    }

    // --- reading a link --------------------------------------------------------------------------------------------

    fun check(url: String) {
        if (_state.value.phase == DownloadPhase.DOWNLOADING || _state.value.phase == DownloadPhase.CHECKING) return
        if (url.isBlank()) {
            _state.update { it.copy(messageRes = R.string.dl_error_empty) }
            return
        }
        _state.update { it.copy(phase = DownloadPhase.CHECKING, messageRes = null, detail = null, items = emptyList()) }
        job = scope.launch {
            val found = try {
                YtDlpEngine.fetch(context, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val res = when (YtDlpEngine.classify(e)) {
                    YtDlpEngine.FailureKind.BOT -> R.string.dl_error_bot
                    YtDlpEngine.FailureKind.DRM -> R.string.dl_error_drm
                    YtDlpEngine.FailureKind.OTHER -> R.string.dl_error_engine
                }
                _state.update { it.copy(phase = DownloadPhase.IDLE, messageRes = res, detail = YtDlpEngine.describe(e)) }
                return@launch
            }
            // "Update playlist": what is already in the save folder is recognised now, so it is not downloaded again.
            val known = scanKnown()
            // A YouTube Music album whose entries carry no position: the list order IS the album order.
            val numberByOrder = found.size > 1 && found.all { it.trackNumber == null } && found.all { it.album.isNotBlank() }
            if (found.isEmpty()) {
                _state.update { it.copy(phase = DownloadPhase.IDLE, messageRes = R.string.dl_error_none) }
            } else {
                _state.update {
                    it.copy(
                        phase = DownloadPhase.READY,
                        items = found.mapIndexed { index, s ->
                            DownloadItem(
                                id = s.id,
                                title = s.title,
                                artist = s.artist,
                                album = s.album,
                                inFolder = known != null && known.matches(s.title, s.artist),
                                libraryUri = if (known != null && known.matches(s.title, s.artist)) known.libraryUri(s.title, s.artist) else null,
                                trackNumber = (s.trackNumber ?: if (numberByOrder) index + 1 else null)?.toString().orEmpty(),
                            )
                        },
                    )
                }
            }
        }
    }

    // --- downloading -----------------------------------------------------------------------------------------------

    fun start() {
        val s = _state.value
        if (s.phase == DownloadPhase.DOWNLOADING || s.phase == DownloadPhase.CHECKING) return
        if (s.items.none { it.status != ItemStatus.DONE && !it.skipped }) return
        _state.update { st ->
            st.copy(
                phase = DownloadPhase.DOWNLOADING,
                messageRes = null,
                items = st.items.map { if (it.status == ItemStatus.DONE) it else it.copy(status = ItemStatus.WAITING, progress = 0f) },
            )
        }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        job = scope.launch {
            try {
                runQueue()
            } finally {
                _runs.update { it + 1 }
            }
        }
    }

    /** Counts finished download runs (after the last save and the library rescan): lets a caller wait for one. */
    private val _runs = MutableStateFlow(0)
    val runs: StateFlow<Int> = _runs.asStateFlow()

    /**
     * Puts ready-made songs (found elsewhere, e.g. by the Spotify screen) into the list, replacing what is there, so
     * that [start] downloads them with the settings of the Download screen. False when a download or read is busy.
     */
    fun loadItems(items: List<DownloadItem>): Boolean {
        val phase = _state.value.phase
        if (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.CHECKING) return false
        _state.update { it.copy(phase = DownloadPhase.READY, items = items, messageRes = null, detail = null) }
        return true
    }

    val isBusy: Boolean
        get() = _state.value.phase.let { it == DownloadPhase.DOWNLOADING || it == DownloadPhase.CHECKING }

    fun stop() {
        YtDlpEngine.cancel(PROCESS_ID)
        job?.cancel()
        _state.update { st ->
            st.copy(
                phase = DownloadPhase.READY,
                items = st.items.map { if (it.status == ItemStatus.RUNNING) it.copy(status = ItemStatus.WAITING, progress = 0f) else it },
            )
        }
    }

    private suspend fun runQueue() {
        val cache = File(context.cacheDir, "yt-downloads").apply { mkdirs() }
        var savedAny = false
        try {
            for (id in _state.value.items.filter { it.status != ItemStatus.DONE && !it.skipped }.map { it.id }) {
                val item = _state.value.items.firstOrNull { it.id == id } ?: continue
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                edit(id) { it.copy(status = ItemStatus.RUNNING, progress = 0f, note = null) }
                var error: String? = null
                val ok = try {
                    val prepared = prepare(item, cache)
                    val s = _state.value
                    if (s.findReplayGain && s.findAlbumGain && prepared.album.isNotBlank() && prepared.blocks != null) {
                        // Songs of an album are saved together at the end, once the album value is known.
                        pending.add(prepared)
                        true
                    } else {
                        save(prepared)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = YtDlpEngine.describe(e)
                    false
                }
                if (!ok && error == null) error = context.getString(R.string.dl_error_save)
                edit(id) {
                    it.copy(status = if (ok) ItemStatus.DONE else ItemStatus.FAILED, progress = if (ok) 1f else 0f, error = if (ok) null else error)
                }
                if (ok) savedAny = true
            }
        } finally {
            // Also after Stop or an error: what was already downloaded is still saved (with a partial album value).
            if (flushPending()) savedAny = true
            cache.listFiles()?.forEach { runCatching { it.delete() } }
            _state.update { it.copy(phase = DownloadPhase.READY) }
        }
        if (savedAny) runCatching { rescan() }
    }

    /** A downloaded, tagged song waiting in the cache. [blocks] = its loudness blocks (null = not measured). */
    private class Prepared(
        val id: String,
        val file: File,
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val blocks: DoubleArray?,
    )

    /** Songs held back until their album's ReplayGain can be written (see [flushPending]). */
    private val pending = ArrayList<Prepared>()

    private fun save(p: Prepared): Boolean = sink.save(p.file, p.fileName, p.title, p.artist, p.album, settings.downloadFolder.value)

    /** Writes the album ReplayGain into every song that shares an album (two or more), then saves them all. */
    private fun flushPending(): Boolean {
        if (pending.isEmpty()) return false
        val batch = pending.toList()
        pending.clear()
        var saved = false
        for (group in batch.groupBy { it.album.trim().lowercase() }.values) {
            val gain = if (group.size >= 2) LoudnessAnalyzer.gainText(Ebur128.integrated(group.mapNotNull { it.blocks })) else null
            for (p in group) {
                if (gain != null) runCatching { FileTagEngine.write(p.file, AudioContainer.MP3, mapOf(MetadataField.REPLAYGAIN_ALBUM to gain)) }
                if (save(p)) {
                    saved = true
                } else {
                    edit(p.id) { it.copy(status = ItemStatus.FAILED, progress = 0f, error = context.getString(R.string.dl_error_save)) }
                }
            }
        }
        return saved
    }

    private fun prepare(item: DownloadItem, cache: File): Prepared {
        val downloaded = YtDlpEngine.downloadMp3(context, item.id, cache, PROCESS_ID) { p ->
            edit(item.id) { it.copy(progress = (p / 100f).coerceIn(0f, 1f)) }
        }
        val mp3 = downloaded.file
        val title = item.title.trim().ifBlank { item.id }
        val artist = item.artist.trim()
        // What the user typed wins; what is blank is filled from what YouTube itself states for the song.
        val album = item.album.trim().ifBlank { downloaded.info.album }
        val year = item.year.trim().ifBlank { downloaded.info.year }
        val track = item.trackNumber.trim().ifBlank { downloaded.info.trackNumber?.toString().orEmpty() }
        val albumArtist = DownloadTags.albumArtistFor(artist, item.albumArtist, _state.value.albumArtistSame)
        val loudness = if (_state.value.findReplayGain) LoudnessAnalyzer.analyze(mp3) { !Thread.currentThread().isInterrupted } else null
        val gain = LoudnessAnalyzer.gainText(loudness?.integratedLufs)
        val changes = DownloadTags.changes(title, artist, album, item.genre.trim(), year, track, albumArtist)
            .let { if (gain != null) it + (MetadataField.REPLAYGAIN_TRACK to gain) else it }
        // Everything the user chose goes INTO the file, so the library shows exactly what was typed.
        try {
            FileTagEngine.write(mp3, AudioContainer.MP3, changes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            edit(item.id) { it.copy(note = YtDlpEngine.describe(e)) }
        }
        return Prepared(item.id, mp3, YtDlpEngine.safeFileName(item.title, item.id), title, artist, album, loudness?.blocks)
    }

    // --- yt-dlp upkeep ---------------------------------------------------------------------------------------------

    /** Reads the sign-in helper switches (called when the screen opens, never at app start). */
    fun loadExtras() {
        scope.launch {
            _state.update {
                it.copy(
                    ytNightly = YtDlpExtras.nightly(context),
                    ytFallback = YtDlpExtras.fallbackClients(context),
                    ytCookies = YtDlpExtras.cookiesEnabled(context),
                    ytHasCookies = YtDlpExtras.hasCookies(context),
                )
            }
        }
    }

    /** "Back to stable": chooses the stable channel and updates right away. */
    fun backToStable() {
        setYtNightly(false)
        updateEngine()
    }

    fun setYtNightly(value: Boolean) {
        YtDlpExtras.setNightly(context, value)
        _state.update { it.copy(ytNightly = value) }
    }

    fun setYtFallback(value: Boolean) {
        YtDlpExtras.setFallbackClients(context, value)
        _state.update { it.copy(ytFallback = value) }
    }

    fun setYtCookies(value: Boolean) {
        YtDlpExtras.setCookiesEnabled(context, value)
        _state.update { it.copy(ytCookies = value) }
    }

    fun importCookies(uri: android.net.Uri) {
        scope.launch {
            val ok = YtDlpExtras.importCookies(context, uri)
            _state.update { it.copy(ytHasCookies = YtDlpExtras.hasCookies(context), messageRes = if (ok) null else R.string.dl_cookies_bad, detail = null) }
        }
    }

    fun clearCookies() {
        YtDlpExtras.clearCookies(context)
        _state.update { it.copy(ytHasCookies = false) }
    }

    fun loadVersion() {
        scope.launch {
            val version = YtDlpEngine.installedVersion(context)
            _state.update { it.copy(engineVersion = version, engineChannel = YtDlpExtras.channelFor(context, version)) }
        }
    }

    fun updateEngine() {
        if (_state.value.updatingEngine) return
        _state.update { it.copy(updatingEngine = true, messageRes = null, detail = null) }
        scope.launch {
            val result = YtDlpEngine.update(context)
            val version = YtDlpEngine.installedVersion(context)
            _state.update {
                it.copy(
                    updatingEngine = false,
                    engineVersion = version,
                    engineChannel = YtDlpExtras.channelFor(context, version),
                    messageRes = when {
                        result.error != null -> R.string.dl_update_failed
                        result.alreadyLatest -> R.string.dl_updated
                        else -> R.string.dl_updated_new
                    },
                    detail = result.error,
                )
            }
        }
    }

    private companion object {
        const val PROCESS_ID = "localplayer-download"
    }
}
