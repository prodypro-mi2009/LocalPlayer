package com.localplayer.music.ui

import androidx.core.net.toUri
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.metadata.MetadataChanges
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.data.metadata.MetadataValidation
import com.localplayer.music.playback.MediaItemFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class BulkEditState(
    val tracks: List<TrackListItem>? = null,
    val values: Map<MetadataField, String> = emptyMap(),
    val fieldErrors: Map<MetadataField, String> = emptyMap(),
    val nothingToSave: Boolean = false,
    val running: Boolean = false,
    val done: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
    val cancelled: Boolean = false,
    /** Technical reason of the first failure, to show under the result. */
    val firstError: String = "",
)

/**
 * Writes the same tag values into every song of an album. Each file goes through the same verified pipeline as the
 * single-song editor (working copy, check, replace, read back, restore on failure). Only the fields the user filled in
 * are written; an empty field means "leave as is". One consent dialog covers all files on Android 11+.
 */
class BulkEditViewModel(private val container: AppContainer, private val albumGroupKey: String) : ViewModel() {
    private val repository = container.metadata
    private val player = container.playerConnection

    private val _state = MutableStateFlow(BulkEditState())
    val state: StateFlow<BulkEditState> = _state.asStateFlow()

    private val _consent = Channel<IntentSender>(Channel.BUFFERED)
    val consent: Flow<IntentSender> = _consent.receiveAsFlow()

    private var pending: MetadataChanges? = null

    init {
        viewModelScope.launch { _state.value = _state.value.copy(tracks = container.library.albumTracks(albumGroupKey).first()) }
    }

    fun onValueChange(field: MetadataField, text: String) {
        _state.value = _state.value.copy(values = _state.value.values + (field to text), fieldErrors = _state.value.fieldErrors - field, nothingToSave = false)
    }

    fun save() {
        val s = _state.value
        val tracks = s.tracks.orEmpty()
        if (s.running || tracks.isEmpty()) return
        val changes: MetadataChanges = s.values.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }
        val errors = changes.mapNotNull { (f, v) -> MetadataValidation.check(f, v)?.let { f to it } }.toMap()
        if (errors.isNotEmpty()) { _state.value = s.copy(fieldErrors = errors); return }
        if (changes.isEmpty()) { _state.value = s.copy(nothingToSave = true); return }
        _state.value = s.copy(running = true, done = 0, failed = 0, finished = false, cancelled = false, firstError = "")
        viewModelScope.launch {
            val sender = runCatching { repository.bulkWriteConsent(tracks.map { it.uri.toUri() }) }.getOrNull()
            if (sender != null) {
                pending = changes
                _consent.send(sender)
            } else {
                run(tracks, changes)
            }
        }
    }

    fun onConsentResult(granted: Boolean) {
        val changes = pending ?: return
        pending = null
        if (!granted) {
            _state.value = _state.value.copy(running = false, cancelled = true)
            return
        }
        viewModelScope.launch { run(_state.value.tracks.orEmpty(), changes) }
    }

    private suspend fun run(tracks: List<TrackListItem>, changes: MetadataChanges) {
        var done = 0
        var failed = 0
        var firstError = ""
        for (t in tracks) {
            val mediaId = t.id.toString()
            val handle = player.beginFileEdit(mediaId)
            try {
                repository.save(t.id, changes)
                done++
            } catch (e: Exception) {
                failed++
                if (firstError.isEmpty()) firstError = (e as? com.localplayer.music.data.metadata.MetadataException)?.detail ?: e.message.orEmpty()
            }
            val ctx = container.localizedContext()
            val fresh = container.library.trackItem(t.id)?.let {
                MediaItemFactory.create(it, ctx.getString(R.string.unknown_artist), ctx.getString(R.string.unknown_album))
            }
            player.finishFileEdit(handle, mediaId, fresh)
            _state.value = _state.value.copy(done = done, failed = failed)
        }
        _state.value = _state.value.copy(running = false, finished = true, firstError = firstError)
    }
}
