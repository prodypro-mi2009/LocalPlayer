package com.localplayer.music.ui

import androidx.core.net.toUri
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackEntity
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.effects.Ebur128
import com.localplayer.music.data.effects.LoudnessAnalyzer
import com.localplayer.music.data.library.SongSort
import com.localplayer.music.data.metadata.MetadataChanges
import com.localplayer.music.data.metadata.MetadataException
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.playback.MediaItemFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryGainPhase { IDLE, LOOKING, READY, RUNNING, DONE }

data class LibraryGainState(
    val phase: LibraryGainPhase = LibraryGainPhase.IDLE,
    /** While looking: songs checked so far / all songs. */
    val looked: Int = 0,
    val total: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val albumsDone: Int = 0,
    val songsDone: Int = 0,
    val failed: Int = 0,
    val current: String = "",
    val cancelled: Boolean = false,
    val firstError: String = "",
    /** Peak mode: also measure songs that already have peak tags and replace them. */
    val remeasure: Boolean = false,
    /** Songs measured with float samples / with 16-bit samples (there a peak above 1.0 is cut off at 1.0). */
    val floatSongs: Int = 0,
    val shortSongs: Int = 0,
)

/**
 * Settings > ReplayGain > Find ReplayGain for the whole library. It only looks at MP3 and FLAC songs that lack the
 * ReplayGain track or album tag, measures each such song's album (so the album value is right), and writes the values
 * into the files album by album through the same verified write as every tag edit. Nothing starts until the user
 * presses Start, and it can be stopped at any time (what is already written stays).
 */
class GainLibraryViewModel(private val container: AppContainer, private val peakOnly: Boolean = false) : ViewModel() {
    /** [missingTrackPeak] / [missingAlbumPeak]: which of the two peak tags each song lacks (peak mode writes only those). */
    private class AlbumPlan(
        val title: String,
        val tracks: List<TrackListItem>,
        val need: List<TrackListItem>,
        val missingTrackPeak: Set<Long> = emptySet(),
        val missingAlbumPeak: Set<Long> = emptySet(),
    )

    private val repository = container.metadata
    private val player = container.playerConnection

    private val _state = MutableStateFlow(LibraryGainState())
    val state: StateFlow<LibraryGainState> = _state.asStateFlow()

    private val _consent = Channel<IntentSender>(Channel.BUFFERED)
    val consent: Flow<IntentSender> = _consent.receiveAsFlow()

    private var plan: List<AlbumPlan> = emptyList()
    private var job: Job? = null
    private var consentWaiter: CompletableDeferred<Boolean>? = null

    fun setRemeasure(on: Boolean) {
        val phase = _state.value.phase
        if (phase == LibraryGainPhase.LOOKING || phase == LibraryGainPhase.RUNNING) return
        // A new choice invalidates the list that was found with the old one.
        plan = emptyList()
        _state.value = LibraryGainState(remeasure = on)
    }

    fun look() {
        if (_state.value.phase == LibraryGainPhase.LOOKING || _state.value.phase == LibraryGainPhase.RUNNING) return
        val remeasure = peakOnly && _state.value.remeasure
        _state.value = LibraryGainState(phase = LibraryGainPhase.LOOKING, remeasure = remeasure)
        job = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { buildPlan(remeasure) }
            plan = found
            _state.value = LibraryGainState(
                phase = LibraryGainPhase.READY,
                remeasure = remeasure,
                albums = found.size,
                songs = found.sumOf { it.need.size },
            )
        }
    }

    private fun writable(e: TrackEntity?): Boolean {
        if (e == null) return false
        val mime = e.mimeType.orEmpty().lowercase()
        val name = e.displayName.lowercase()
        return mime == "audio/mpeg" || mime == "audio/mp3" || mime.contains("flac") || name.endsWith(".mp3") || name.endsWith(".flac")
    }

    private suspend fun buildPlan(remeasure: Boolean): List<AlbumPlan> {
        val all = container.library.songs(SongSort.TITLE).first()
        _state.value = _state.value.copy(total = all.size)
        val dao = container.database.trackDao()
        val result = ArrayList<AlbumPlan>()
        var looked = 0
        val groups = all.groupBy { it.albumGroupKey?.takeIf { key -> key.isNotBlank() } ?: "single:${it.id}" }
        for (songs in groups.values) {
            val eligible = songs.filter { writable(dao.getEntity(it.id)) }
            val missingTrack = HashSet<Long>()
            val missingAlbum = HashSet<Long>()
            val need = eligible.filter {
                val info = container.replayGain.info(it.id, it.uri)
                if (peakOnly) {
                    // Re-measuring replaces tags that already exist (for example a peak cut off at 1.0 by a 16-bit decoder).
                    if (remeasure || info.trackPeak == null) missingTrack.add(it.id)
                    if (remeasure || info.albumPeak == null) missingAlbum.add(it.id)
                    remeasure || info.trackPeak == null || info.albumPeak == null
                } else {
                    info.trackGainDb == null || info.albumGainDb == null
                }
            }
            if (need.isNotEmpty()) {
                result.add(AlbumPlan(songs.first().album.orEmpty().ifBlank { songs.first().title }, eligible, need, missingTrack, missingAlbum))
            }
            looked += songs.size
            _state.value = _state.value.copy(looked = looked)
        }
        return result
    }

    fun start() {
        if (_state.value.phase != LibraryGainPhase.READY || plan.isEmpty()) return
        _state.value = _state.value.copy(phase = LibraryGainPhase.RUNNING, albumsDone = 0, songsDone = 0, failed = 0, cancelled = false, firstError = "", floatSongs = 0, shortSongs = 0)
        job = viewModelScope.launch {
            val batch = ArrayList<AlbumPlan>()
            var writes = 0
            var stopped = false
            for (album in plan) {
                batch.add(album)
                writes += album.need.size
                if (writes >= BATCH_SONGS) {
                    if (!runBatch(batch)) { stopped = true; break }
                    batch.clear()
                    writes = 0
                }
            }
            if (!stopped && batch.isNotEmpty()) stopped = !runBatch(batch)
            _state.value = _state.value.copy(phase = LibraryGainPhase.DONE, cancelled = stopped, current = "")
        }
    }

    fun stop() {
        job?.cancel()
        consentWaiter?.complete(false)
        _state.value = _state.value.copy(phase = LibraryGainPhase.DONE, cancelled = true, current = "")
    }

    fun onConsentResult(granted: Boolean) {
        consentWaiter?.complete(granted)
    }

    /**
     * One batch: the single write consent is asked FIRST, then every album is measured and its songs are written right
     * away (so a stop, a crash or an empty battery loses at most the album being measured). False = the user said no.
     */
    private suspend fun runBatch(batch: List<AlbumPlan>): Boolean {
        val runningJob = kotlin.coroutines.coroutineContext[Job]
        val active = { runningJob?.isActive != false }
        val context = container.appContext
        val uris = batch.flatMap { it.need }.map { it.uri.toUri() }
        val sender = runCatching { repository.bulkWriteConsent(uris) }.getOrNull()
        if (sender != null) {
            val waiter = CompletableDeferred<Boolean>()
            consentWaiter = waiter
            _consent.send(sender)
            val granted = waiter.await()
            consentWaiter = null
            if (!granted) return false
        }
        for (album in batch) {
            _state.value = _state.value.copy(current = album.title)
            val gains = HashMap<Long, String?>()
            val peaks = HashMap<Long, String?>()
            var albumPeak = 0f
            val blocks = ArrayList<DoubleArray>()
            withContext(Dispatchers.Default) {
                for (t in album.tracks) {
                    val loudness = LoudnessAnalyzer.analyze(context, t.uri.toUri(), active)
                    loudness?.let { blocks.add(it.blocks) }
                    gains[t.id] = LoudnessAnalyzer.gainText(loudness?.integratedLufs)
                    peaks[t.id] = loudness?.let { LoudnessAnalyzer.peakText(it.peak) }
                    if (loudness != null && loudness.peak > albumPeak) albumPeak = loudness.peak
                    if (loudness != null) {
                        val s = _state.value
                        _state.value = if (loudness.floatSamples) s.copy(floatSongs = s.floatSongs + 1) else s.copy(shortSongs = s.shortSongs + 1)
                    }
                }
            }
            val albumGain = LoudnessAnalyzer.gainText(Ebur128.integrated(blocks))
            val albumPeakText = LoudnessAnalyzer.peakText(albumPeak)
            for (t in album.need) {
                val changes: MetadataChanges = buildMap {
                    if (peakOnly) {
                        // Only the peak tags that were asked for (the missing ones, or all when re-measuring).
                        if (t.id in album.missingTrackPeak) peaks[t.id]?.let { put(MetadataField.REPLAYGAIN_TRACK_PEAK, it) }
                        if (t.id in album.missingAlbumPeak) albumPeakText?.let { put(MetadataField.REPLAYGAIN_ALBUM_PEAK, it) }
                    } else {
                        gains[t.id]?.let { put(MetadataField.REPLAYGAIN_TRACK, it) }
                        albumGain?.let { put(MetadataField.REPLAYGAIN_ALBUM, it) }
                    }
                }
                if (changes.isEmpty()) {
                    val s = _state.value
                    _state.value = s.copy(
                        failed = s.failed + 1,
                        firstError = s.firstError.ifEmpty { "${t.title}: nothing could be measured, so nothing was written." },
                    )
                    continue
                }
                val mediaId = t.id.toString()
                val handle = player.beginFileEdit(mediaId)
                try {
                    repository.save(t.id, changes)
                    _state.value = _state.value.copy(songsDone = _state.value.songsDone + 1)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    player.finishFileEdit(handle, mediaId, null)
                    throw e
                } catch (e: Exception) {
                    val s = _state.value
                    _state.value = s.copy(
                        failed = s.failed + 1,
                        firstError = s.firstError.ifEmpty { (e as? MetadataException)?.detail ?: e.message.orEmpty() },
                    )
                }
                val ctx = container.localizedContext()
                val fresh = container.library.trackItem(t.id)?.let {
                    MediaItemFactory.create(it, ctx.getString(R.string.unknown_artist), ctx.getString(R.string.unknown_album))
                }
                player.finishFileEdit(handle, mediaId, fresh)
            }
            _state.value = _state.value.copy(albumsDone = _state.value.albumsDone + 1)
        }
        return true
    }

    private companion object {
        /** One consent dialog covers at most about this many songs. */
        const val BATCH_SONGS = 400
    }
}
