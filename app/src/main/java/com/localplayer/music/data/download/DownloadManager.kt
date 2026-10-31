package com.localplayer.music.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.localplayer.music.R
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.metadata.AudioContainer
import com.localplayer.music.data.metadata.FileTagEngine
import com.localplayer.music.data.metadata.MetadataField
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
    /** Ticked by the user: the "fill in for many songs" action then touches only the ticked songs. */
    val selected: Boolean = false,
    val status: ItemStatus = ItemStatus.WAITING,
    val progress: Float = 0f,
    val error: String? = null,
    /** A saved song whose tags could not be written completely (the song itself is saved). */
    val note: String? = null,
)

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
    /** Write each song's artist also as the "album artist" tag. */
    val albumArtistSame: Boolean = true,
)

/**
 * App-scoped (it outlives the screen): edits to the list, the sequential download queue, tagging, saving and the final
 * library rescan. A started download is kept alive in the background by [DownloadService].
 */
class DownloadManager(
    private val context: Context,
    private val settings: AppSettings,
    private val rescan: suspend () -> Unit,
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
    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()
    private var job: Job? = null
    private val sink = DownloadSink(context)

    // --- editing ---------------------------------------------------------------------------------------------------

    fun setTitle(id: String, title: String) = edit(id) { it.copy(title = title) }

    fun setArtist(id: String, artist: String) = edit(id) { it.copy(artist = artist) }

    fun setTrackNumber(id: String, value: String) = edit(id) { it.copy(trackNumber = value.filter(Char::isDigit).take(4)) }

    fun setSelected(id: String, on: Boolean) = edit(id) { it.copy(selected = on) }

    fun selectAll(on: Boolean) = _state.update { s ->
        s.copy(items = s.items.map { if (it.status == ItemStatus.DONE) it else it.copy(selected = on) })
    }

    fun setAlbumArtistSame(on: Boolean) = _state.update { it.copy(albumArtistSame = on) }

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
        _state.value = DownloadState()
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
                _state.update { it.copy(phase = DownloadPhase.IDLE, messageRes = R.string.dl_error_engine, detail = YtDlpEngine.describe(e)) }
                return@launch
            }
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
        if (s.items.none { it.status != ItemStatus.DONE }) return
        _state.update { st ->
            st.copy(
                phase = DownloadPhase.DOWNLOADING,
                messageRes = null,
                items = st.items.map { if (it.status == ItemStatus.DONE) it else it.copy(status = ItemStatus.WAITING, progress = 0f) },
            )
        }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        job = scope.launch { runQueue() }
    }

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
            for (id in _state.value.items.filter { it.status != ItemStatus.DONE }.map { it.id }) {
                val item = _state.value.items.firstOrNull { it.id == id } ?: continue
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                edit(id) { it.copy(status = ItemStatus.RUNNING, progress = 0f, note = null) }
                var error: String? = null
                val ok = try {
                    downloadOne(item, cache)
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
            cache.listFiles()?.forEach { runCatching { it.delete() } }
            _state.update { it.copy(phase = DownloadPhase.READY) }
        }
        if (savedAny) runCatching { rescan() }
    }

    private fun downloadOne(item: DownloadItem, cache: File): Boolean {
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
        val changes = DownloadTags.changes(title, artist, album, item.genre.trim(), year, track, albumArtistSame = true)
        // Everything the user chose goes INTO the file, so the library shows exactly what was typed.
        try {
            FileTagEngine.write(mp3, AudioContainer.MP3, changes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            edit(item.id) { it.copy(note = YtDlpEngine.describe(e)) }
        }
        val fileName = YtDlpEngine.safeFileName(item.title, item.id)
        return sink.save(mp3, fileName, title, artist, album, settings.downloadFolder.value)
    }

    // --- yt-dlp upkeep ---------------------------------------------------------------------------------------------

    fun loadVersion() {
        scope.launch { _state.update { it.copy(engineVersion = YtDlpEngine.installedVersion(context)) } }
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
