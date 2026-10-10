package com.localplayer.music.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** One progress state and one lock shared by both scanners, so two scans never run at once. */
class ScanCoordinator {
    private val mutex = Mutex()
    private val rerunRequested = AtomicBoolean(false)
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun update(transform: (ScanState) -> ScanState) = _state.update(transform)

    /** Starts a pass; this also clears the error of a previous failed pass (that is what "retry" does). */
    fun begin() {
        _state.value = ScanState(isScanning = true)
    }

    /** Records that the current pass failed. The existing index is not touched by this call. */
    fun fail(kind: ScanFailureKind, detail: String? = null) {
        _state.update { it.copy(error = ScanError(kind, detail)) }
    }

    /** Runs [pass]. If a scan is already running, it is repeated once when that one finishes. */
    suspend fun run(pass: suspend () -> Unit) {
        if (!mutex.tryLock()) {
            rerunRequested.set(true)
            return
        }
        try {
            do {
                rerunRequested.set(false)
                try {
                    pass()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed pass must never crash the app. The rows written so far are consistent
                    // (each batch is written whole) and the rest of the index is untouched; the failure is
                    // exposed so the UI can explain it and offer a retry.
                    fail(ScanFailureKind.UNEXPECTED, e.message ?: e.javaClass.simpleName)
                }
            } while (rerunRequested.get())
        } finally {
            _state.update { it.copy(isScanning = false) }
            mutex.unlock()
        }
    }
}
