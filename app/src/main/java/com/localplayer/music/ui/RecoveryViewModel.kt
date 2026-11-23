package com.localplayer.music.ui

import androidx.core.net.toUri
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.metadata.MetadataRepository.InterruptedWrite
import com.localplayer.music.data.metadata.WriteAccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class RecoveryState(val item: InterruptedWrite? = null, val busy: Boolean = false)

sealed interface RecoveryEvent {
    data class RequestConsent(val sender: IntentSender) : RecoveryEvent
    data object RequestLegacyPermission : RecoveryEvent
    data class Message(@param:StringRes val res: Int) : RecoveryEvent
}

/**
 * Offers the original file back after a tag save was cut off (app killed, phone off) and left the song damaged.
 * It looks once, a few seconds after the app opened and off the main thread; with nothing to recover that is a single
 * directory check. Nothing is changed without the user pressing "Restore".
 */
class RecoveryViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RecoveryState())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()
    private val _events = Channel<RecoveryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var queue: List<InterruptedWrite> = emptyList()

    init {
        viewModelScope.launch {
            delay(5_000)
            queue = container.metadata.interruptedWrites()
            showNext()
        }
    }

    private fun showNext() {
        _state.value = RecoveryState(item = queue.firstOrNull())
    }

    /** "Later": nothing is deleted; the question comes again at the next start. */
    fun later() {
        queue = emptyList()
        showNext()
    }

    fun restore() {
        val item = _state.value.item ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            when (val access = container.metadata.checkWriteAccess(item.uri.toUri())) {
                WriteAccess.Granted -> doRestore(item)
                is WriteAccess.NeedsConsent -> _events.send(RecoveryEvent.RequestConsent(access.intentSender))
                WriteAccess.NeedsLegacyPermission -> _events.send(RecoveryEvent.RequestLegacyPermission)
                is WriteAccess.Denied -> fail()
            }
        }
    }

    fun onAccessResult(granted: Boolean) {
        val item = _state.value.item ?: return
        if (!granted) {
            fail()
            return
        }
        viewModelScope.launch { doRestore(item) }
    }

    private suspend fun doRestore(item: InterruptedWrite) {
        if (container.metadata.restoreInterrupted(item.id)) {
            _events.send(RecoveryEvent.Message(R.string.recovery_done))
            queue = queue.drop(1)
            showNext()
        } else {
            fail()
        }
    }

    private fun fail() {
        _state.value = _state.value.copy(busy = false)
        _events.trySend(RecoveryEvent.Message(R.string.recovery_failed))
    }
}
