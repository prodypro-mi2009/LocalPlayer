package com.localplayer.music.ui

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.data.metadata.FailureKind
import com.localplayer.music.data.metadata.MetadataChanges
import com.localplayer.music.data.metadata.MetadataException
import com.localplayer.music.data.metadata.MetadataField
import com.localplayer.music.data.metadata.MetadataValidation
import com.localplayer.music.data.metadata.TrackMetadata
import com.localplayer.music.data.metadata.WriteAccess
import com.localplayer.music.data.metadata.WriteCapability
import com.localplayer.music.playback.MediaItemFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** A failure shown to the user: what kind, plus the technical detail. */
data class EditIssue(val kind: FailureKind, val detail: String)

data class EditUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val displayName: String = "",
    val formatLabel: String = "",
    val capability: WriteCapability? = null,
    /** True when the values were read from the file itself (false = read-only formats shown from the index). */
    val fromFile: Boolean = false,
    val original: TrackMetadata? = null,
    val values: Map<MetadataField, String> = emptyMap(),
    val fieldErrors: Map<MetadataField, String> = emptyMap(),
    val issue: EditIssue? = null,
    val noChanges: Boolean = false,
    /** The library row of this track (for the primary-artist classification). */
    val track: TrackListItem? = null,
) {
    val canEdit: Boolean get() = capability?.canWrite == true && fromFile
}

sealed interface EditEvent {
    data class RequestConsent(val sender: IntentSender) : EditEvent
    data object RequestLegacyPermission : EditEvent
    data object Saved : EditEvent
}

/**
 * Edit Metadata: loads the REAL tags of the file, and on Save writes the changed fields back into
 * the file (MetadataRepository), verifies by re-reading the file, and only then reports success.
 * Cancel simply leaves the screen: nothing is written and no permission is requested.
 */
class EditMetadataViewModel(
    private val container: AppContainer,
    private val trackId: Long,
) : ViewModel() {

    private val repository = container.metadata
    private val player = container.playerConnection

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    private var pendingChanges: MetadataChanges? = null
    private var trackUri: String? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch { reloadFromFile() }
    }

    /** (Re)reads the REAL tags of the file. Also used after a save so the screen never shows stale data. */
    private suspend fun reloadFromFile() {
        run {
            try {
                val loaded = repository.load(trackId)
                val item = container.library.trackItem(trackId)
                trackUri = item?.uri
                _state.value = EditUiState(
                    track = item,
                    loading = false,
                    displayName = loaded.displayName,
                    formatLabel = loaded.metadata.container.label,
                    capability = loaded.capability,
                    fromFile = loaded.fromFile,
                    original = loaded.metadata,
                    values = MetadataField.entries.associateWith { loaded.metadata.value(it).orEmpty() },
                )
            } catch (e: MetadataException) {
                _state.value = EditUiState(loading = false, issue = EditIssue(e.kind, e.detail))
            } catch (e: Exception) {
                _state.value = EditUiState(loading = false, issue = EditIssue(FailureKind.UNKNOWN, e.message.orEmpty()))
            }
        }
    }

    /**
     * Library classification only: files this song under another artist in Artists. The file's Artist tag
     * is not touched (blank = back to the automatic classification).
     */
    fun setPrimaryArtist(name: String?) {
        val uri = trackUri ?: return
        viewModelScope.launch {
            container.library.setPrimaryArtist(uri, name)
            _state.value = _state.value.copy(track = container.library.trackItem(trackId))
        }
    }

    fun onValueChange(field: MetadataField, text: String) {
        _state.value = _state.value.copy(
            values = _state.value.values + (field to text),
            fieldErrors = _state.value.fieldErrors - field,
            issue = null,
            noChanges = false,
        )
    }

    fun save() {
        val current = _state.value
        val original = current.original ?: return
        if (!current.canEdit || current.saving) return

        val errors = MetadataField.entries.mapNotNull { field ->
            MetadataValidation.check(field, current.values[field])?.let { field to it }
        }.toMap()
        if (errors.isNotEmpty()) {
            _state.value = current.copy(fieldErrors = errors, issue = EditIssue(FailureKind.INVALID_INPUT, ""))
            return
        }
        val changes = MetadataValidation.changesFrom(original, current.values)
        if (changes.isEmpty()) {
            _state.value = current.copy(noChanges = true)
            return
        }
        viewModelScope.launch { proceed(changes) }
    }

    private suspend fun proceed(changes: MetadataChanges) {
        val uri = trackUri ?: container.library.trackItem(trackId)?.uri
        if (uri == null) {
            fail(FailureKind.FILE_UNAVAILABLE, "")
            return
        }
        _state.value = _state.value.copy(saving = true, issue = null, noChanges = false)
        when (val access = repository.checkWriteAccess(Uri.parse(uri))) {
            WriteAccess.Granted -> write(changes)
            is WriteAccess.NeedsConsent -> {
                pendingChanges = changes
                _events.send(EditEvent.RequestConsent(access.intentSender))
            }
            WriteAccess.NeedsLegacyPermission -> {
                pendingChanges = changes
                _events.send(EditEvent.RequestLegacyPermission)
            }
            is WriteAccess.Denied -> fail(access.kind, access.detail)
        }
    }

    /** Result of the system write-consent dialog (or of the legacy storage permission request). */
    fun onAccessResult(granted: Boolean) {
        val changes = pendingChanges ?: return
        pendingChanges = null
        if (!granted) {
            fail(FailureKind.USER_CANCELLED, "")
            return
        }
        viewModelScope.launch {
            val uri = trackUri ?: return@launch fail(FailureKind.FILE_UNAVAILABLE, "")
            when (val access = repository.checkWriteAccess(Uri.parse(uri))) {
                WriteAccess.Granted -> write(changes)
                is WriteAccess.Denied -> fail(access.kind, access.detail)
                else -> fail(FailureKind.WRITE_PERMISSION_DENIED, "")
            }
        }
    }

    private suspend fun write(changes: MetadataChanges) {
        val mediaId = trackId.toString()
        val handle = player.beginFileEdit(mediaId)
        try {
            repository.save(trackId, changes)
            // Fresh item built from the refreshed cache, so queue + notification show the new tags.
            player.finishFileEdit(handle, mediaId, freshMediaItem())
            // Re-read what is REALLY in the file now, so reopening shows the saved content and a second save
            // compares against it (never against the pre-save snapshot).
            reloadFromFile()
            _state.value = _state.value.copy(saving = false)
            _events.send(EditEvent.Saved)
        } catch (e: MetadataException) {
            player.finishFileEdit(handle, mediaId, freshMediaItem())
            fail(e.kind, e.detail)
        } catch (e: Exception) {
            player.finishFileEdit(handle, mediaId, freshMediaItem())
            fail(FailureKind.UNKNOWN, e.message.orEmpty())
        }
    }

    private suspend fun freshMediaItem() = container.library.trackItem(trackId)?.let {
        MediaItemFactory.create(
            it,
            container.localizedContext().getString(R.string.unknown_artist),
            container.localizedContext().getString(R.string.unknown_album),
        )
    }

    private fun fail(kind: FailureKind, detail: String) {
        _state.value = _state.value.copy(saving = false, issue = EditIssue(kind, detail))
    }
}
