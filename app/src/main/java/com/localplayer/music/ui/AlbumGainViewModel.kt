package com.localplayer.music.ui

import androidx.core.net.toUri
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.effects.Ebur128
import com.localplayer.music.data.effects.LoudnessAnalyzer
import com.localplayer.music.data.metadata.MetadataChanges
import com.localplayer.music.data.metadata.MetadataException
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.playback.MediaItemFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GainPhase { IDLE, MEASURING, READY, WRITING, DONE }

/** One song of the album: [gain] is null when its loudness could not be measured. */
class GainRow(val track: TrackListItem, val gain: String?)

data class AlbumGainState(
    val tracks: List<TrackListItem>? = null,
    val phase: GainPhase = GainPhase.IDLE,
    val progress: Int = 0,
    val rows: List<GainRow> = emptyList(),
    val albumGain: String? = null,
    val done: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val firstError: String = "",
)

/**
 * Album > ReplayGain of all songs. Step 1 measures every song (nothing is written); step 2 writes each song's own
 * track gain and the shared album gain into the files, one by one through the same verified write as every tag edit.
 */
class AlbumGainViewModel(private val container: AppContainer, private val albumGroupKey: String) : ViewModel() {
    private val repository = container.metadata
    private val player = container.playerConnection

    private val _state = MutableStateFlow(AlbumGainState())
    val state: StateFlow<AlbumGainState> = _state.asStateFlow()

    private val _consent = Channel<IntentSender>(Channel.BUFFERED)
    val consent: Flow<IntentSender> = _consent.receiveAsFlow()

    init {
        viewModelScope.launch { _state.value = _state.value.copy(tracks = container.library.albumTracks(albumGroupKey).first()) }
    }

    fun measure() {
        val tracks = _state.value.tracks.orEmpty()
        if (tracks.isEmpty() || _state.value.phase == GainPhase.MEASURING || _state.value.phase == GainPhase.WRITING) return
        _state.value = _state.value.copy(phase = GainPhase.MEASURING, progress = 0, rows = emptyList(), albumGain = null, done = 0, failed = 0, cancelled = false, firstError = "")
        viewModelScope.launch {
            val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            val active = { job?.isActive != false }
            val rows = ArrayList<GainRow>()
            val blocks = ArrayList<DoubleArray>()
            withContext(Dispatchers.Default) {
                for (t in tracks) {
                    val loudness = LoudnessAnalyzer.analyze(container.appContext, t.uri.toUri(), active)
                    if (loudness != null) blocks.add(loudness.blocks)
                    rows.add(GainRow(t, LoudnessAnalyzer.gainText(loudness?.integratedLufs)))
                    _state.value = _state.value.copy(progress = rows.size)
                }
            }
            _state.value = _state.value.copy(
                phase = GainPhase.READY,
                rows = rows,
                albumGain = LoudnessAnalyzer.gainText(Ebur128.integrated(blocks)),
            )
        }
    }

    fun write() {
        val s = _state.value
        if (s.phase != GainPhase.READY || s.albumGain == null) return
        _state.value = s.copy(phase = GainPhase.WRITING, done = 0, failed = 0, cancelled = false, firstError = "")
        viewModelScope.launch {
            val sender = runCatching { repository.bulkWriteConsent(s.rows.map { it.track.uri.toUri() }) }.getOrNull()
            if (sender != null) _consent.send(sender) else run()
        }
    }

    fun onConsentResult(granted: Boolean) {
        if (_state.value.phase != GainPhase.WRITING) return
        if (!granted) {
            _state.value = _state.value.copy(phase = GainPhase.READY, cancelled = true)
            return
        }
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val s = _state.value
        var done = 0
        var failed = 0
        var firstError = ""
        for (row in s.rows) {
            val changes: MetadataChanges = buildMap {
                row.gain?.let { put(MetadataField.REPLAYGAIN_TRACK, it) }
                s.albumGain?.let { put(MetadataField.REPLAYGAIN_ALBUM, it) }
            }
            if (changes.isEmpty()) continue
            val mediaId = row.track.id.toString()
            val handle = player.beginFileEdit(mediaId)
            try {
                repository.save(row.track.id, changes)
                done++
            } catch (e: Exception) {
                failed++
                if (firstError.isEmpty()) firstError = (e as? MetadataException)?.detail ?: e.message.orEmpty()
            }
            val ctx = container.localizedContext()
            val fresh = container.library.trackItem(row.track.id)?.let {
                MediaItemFactory.create(it, ctx.getString(R.string.unknown_artist), ctx.getString(R.string.unknown_album))
            }
            player.finishFileEdit(handle, mediaId, fresh)
            _state.value = _state.value.copy(done = done, failed = failed)
        }
        _state.value = _state.value.copy(phase = GainPhase.DONE, done = done, failed = failed, firstError = firstError)
    }
}
