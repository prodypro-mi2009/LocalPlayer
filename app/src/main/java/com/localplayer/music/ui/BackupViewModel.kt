package com.localplayer.music.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.backup.ApplyResult
import com.localplayer.music.data.backup.ImportPlan
import com.localplayer.music.data.backup.PreparedImport
import com.localplayer.music.data.backup.SettingsBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Preview(val prepared: PreparedImport, val plan: ImportPlan, val restoreActivation: Boolean, val isArchive: Boolean) : BackupUi
    /** [error] = true for failures. [detail] is a technical reason (may be empty). */
    data class Done(val messageRes: Int, val error: Boolean, val detail: String = "", val arg: Int = 0) : BackupUi
}

/** Drives Backup & Restore. Does nothing until the user picks an export or import. */
class BackupViewModel(private val container: AppContainer) : ViewModel() {
    private val manager: SettingsBackupManager = container.settingsBackup
    private val _state = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val state: StateFlow<BackupUi> = _state.asStateFlow()

    fun export(target: Uri, complete: Boolean) {
        if (_state.value is BackupUi.Working) return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = manager.export(target, complete)
            _state.value = if (r.isSuccess) BackupUi.Done(if (complete) R.string.backup_export_ok_complete else R.string.backup_export_ok_json, false)
            else BackupUi.Done(R.string.backup_export_failed, true, r.exceptionOrNull()?.message.orEmpty())
        }
    }

    fun choose(source: Uri) {
        if (_state.value is BackupUi.Working) return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = manager.prepare(source, restoreActivation = false)
            _state.value = r.fold(
                onSuccess = { p -> BackupUi.Preview(p, p.plan, false, p.isArchive) },
                onFailure = { BackupUi.Done(R.string.backup_import_invalid, true, it.message.orEmpty()) },
            )
        }
    }

    fun setRestoreActivation(value: Boolean) {
        val s = _state.value as? BackupUi.Preview ?: return
        val plan = manager.planFor(s.prepared.validation, value, s.plan.restoreBackground, s.plan.restoreFont)
        _state.value = s.copy(plan = plan, restoreActivation = value)
    }

    fun confirm() {
        val s = _state.value as? BackupUi.Preview ?: return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            _state.value = when (val r = manager.apply(s.prepared, s.restoreActivation)) {
                is ApplyResult.Success -> {
                    com.localplayer.music.playback.AudioEffectsHub.refresh()
                    com.localplayer.music.widget.WidgetController.sync(container.appContext)
                    BackupUi.Done(R.string.backup_import_ok, false, arg = r.changed)
                }
                is ApplyResult.Failed -> BackupUi.Done(
                    if (r.restoredPrevious) R.string.backup_import_failed_restored else R.string.backup_import_failed_unsure, true, r.reason,
                )
            }
        }
    }

    fun cancel() {
        (_state.value as? BackupUi.Preview)?.let { manager.discard(it.prepared) }
        _state.value = BackupUi.Idle
    }

    fun dismiss() { _state.value = BackupUi.Idle }

    override fun onCleared() {
        (_state.value as? BackupUi.Preview)?.let { manager.discard(it.prepared) }
    }
}
