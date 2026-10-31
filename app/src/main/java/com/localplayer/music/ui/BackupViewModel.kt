package com.localplayer.music.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.R
import com.localplayer.music.data.backup.ApplyResult
import com.localplayer.music.data.backup.ImportPlan
import com.localplayer.music.data.backup.LibraryApplyResult
import com.localplayer.music.data.backup.LibrarySection
import com.localplayer.music.data.backup.PreparedImport
import com.localplayer.music.data.backup.SettingsBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi

    /**
     * [selected] holds the parts the user ticked: [SETTINGS_PART] and/or the [LibrarySection] keys.
     * [replace] false = add to what is on this phone; true = replace the ticked library parts.
     */
    data class Preview(
        val prepared: PreparedImport,
        val plan: ImportPlan,
        val restoreActivation: Boolean,
        val isArchive: Boolean,
        val selected: Set<String>,
        val replace: Boolean,
    ) : BackupUi

    /** [error] = true for failures. [message] (when not empty) replaces the string resource. [detail] is a technical reason. */
    data class Done(val messageRes: Int, val error: Boolean, val detail: String = "", val arg: Int = 0, val message: String = "") : BackupUi

    companion object {
        const val SETTINGS_PART = "settings"
    }
}

/** Drives Backup & Restore. Does nothing until the user picks an export or import or switches an automatic backup on. */
class BackupViewModel(private val container: AppContainer) : ViewModel() {
    private val manager: SettingsBackupManager = container.settingsBackup
    private val settings = container.settings
    private val _state = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val state: StateFlow<BackupUi> = _state.asStateFlow()

    private val _lastFolderBackup = MutableStateFlow(settings.lastAutoBackupAt)
    val lastFolderBackup: StateFlow<Long> = _lastFolderBackup.asStateFlow()
    private val _lastAndroidCopy = MutableStateFlow(settings.lastAndroidCopyAt)
    val lastAndroidCopy: StateFlow<Long> = _lastAndroidCopy.asStateFlow()

    /** A backup copy that arrived with Android's restore and was not restored or dismissed yet. */
    private val _restoredCopy = MutableStateFlow(container.autoBackup.restoredCopy() != null)
    val restoredCopy: StateFlow<Boolean> = _restoredCopy.asStateFlow()
    private var choseRestoredCopy = false

    fun export(target: Uri, complete: Boolean) {
        if (_state.value is BackupUi.Working) return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = manager.export(target, complete)
            _state.value = if (r.isSuccess) BackupUi.Done(if (complete) R.string.backup_export_ok_complete else R.string.backup_export_ok_json, false)
            else BackupUi.Done(R.string.backup_export_failed, true, r.exceptionOrNull()?.message.orEmpty())
        }
    }

    fun choose(source: Uri) = choose(source, fromRestoredCopy = false)

    private fun choose(source: Uri, fromRestoredCopy: Boolean) {
        if (_state.value is BackupUi.Working) return
        choseRestoredCopy = fromRestoredCopy
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = manager.prepare(source, restoreActivation = false)
            _state.value = r.fold(
                onSuccess = { p ->
                    val selected = LinkedHashSet<String>().apply {
                        add(BackupUi.SETTINGS_PART)
                        p.libraryPreview.forEach { add(it.section) }
                    }
                    BackupUi.Preview(p, p.plan, false, p.isArchive, selected, replace = false)
                },
                onFailure = { BackupUi.Done(R.string.backup_import_invalid, true, it.message.orEmpty()) },
            )
        }
    }

    fun chooseRestoredCopy() {
        val file = container.autoBackup.restoredCopy() ?: run { _restoredCopy.value = false; return }
        choose(Uri.fromFile(file), fromRestoredCopy = true)
    }

    fun dismissRestoredCopy() {
        container.autoBackup.markRestoredCopyHandled()
        _restoredCopy.value = false
        _lastAndroidCopy.value = settings.lastAndroidCopyAt
    }

    fun setRestoreActivation(value: Boolean) {
        val s = _state.value as? BackupUi.Preview ?: return
        val plan = manager.planFor(s.prepared.validation, value, s.plan.restoreBackground, s.plan.restoreFont)
        _state.value = s.copy(plan = plan, restoreActivation = value)
    }

    fun toggleSection(key: String) {
        val s = _state.value as? BackupUi.Preview ?: return
        _state.value = s.copy(selected = if (key in s.selected) s.selected - key else s.selected + key)
    }

    fun setReplace(value: Boolean) {
        val s = _state.value as? BackupUi.Preview ?: return
        _state.value = s.copy(replace = value)
    }

    fun confirm() {
        val s = _state.value as? BackupUi.Preview ?: return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val prepared = s.prepared
            val chosenLibrary = s.selected.filter { it in LibrarySection.all }.toSet()
            val lib = prepared.library

            // Library data first: it reads the cover pictures from the temporary folder that the settings step cleans up.
            var libResult: LibraryApplyResult? = null
            var libError: String? = null
            if (lib != null && chosenLibrary.isNotEmpty()) {
                try {
                    libResult = container.libraryBackup.apply(
                        lib, prepared.resolved, prepared.archive?.covers.orEmpty(), chosenLibrary, s.replace,
                    )
                } catch (e: Exception) {
                    libError = e.message ?: e.javaClass.simpleName
                }
            }
            val settingsPart = BackupUi.SETTINGS_PART in s.selected
            val settingsResult = manager.apply(prepared, s.restoreActivation, applySettings = settingsPart)
            if (settingsPart && settingsResult is ApplyResult.Success) {
                com.localplayer.music.playback.AudioEffectsHub.refresh()
                com.localplayer.music.widget.WidgetController.sync(container.appContext)
            }

            val ctx = container.localizedContext()
            val lines = ArrayList<String>()
            if (settingsPart && settingsResult is ApplyResult.Success) lines += ctx.getString(R.string.backup_res_settings, settingsResult.changed)
            libResult?.let { r ->
                if (LibrarySection.PLAYLISTS in chosenLibrary && lib!!.playlists.isNotEmpty())
                    lines += ctx.getString(R.string.backup_res_playlists, r.playlistsAdded, r.playlistsMerged, r.playlistTracks)
                if (LibrarySection.FAVORITES in chosenLibrary && lib!!.favorites.isNotEmpty())
                    lines += ctx.getString(R.string.backup_res_favorites, r.favorites)
                if (LibrarySection.HISTORY in chosenLibrary && (lib!!.history.isNotEmpty() || lib.days.isNotEmpty()))
                    lines += ctx.getString(R.string.backup_res_history, r.history, r.days)
                if (LibrarySection.RULES in chosenLibrary)
                    lines += ctx.getString(R.string.backup_res_rules, r.overrides, r.hidden, r.excluded)
                if (r.skipped > 0) lines += ctx.getString(R.string.backup_res_skipped, r.skipped)
            }
            val failed = libError != null || settingsResult is ApplyResult.Failed
            val details = ArrayList<String>()
            libError?.let { details += ctx.getString(R.string.backup_res_library_failed, it) }
            (settingsResult as? ApplyResult.Failed)?.let {
                details += ctx.getString(
                    if (it.restoredPrevious) R.string.backup_import_failed_restored else R.string.backup_import_failed_unsure,
                ) + " " + it.reason
            }
            if (!failed && choseRestoredCopy) {
                container.autoBackup.markRestoredCopyHandled()
                _restoredCopy.value = false
                _lastAndroidCopy.value = settings.lastAndroidCopyAt
            }
            _state.value = BackupUi.Done(
                messageRes = if (failed) R.string.backup_import_failed_partial else R.string.backup_import_done,
                error = failed,
                detail = details.joinToString("\n"),
                message = lines.joinToString("\n"),
            )
        }
    }

    fun cancel() {
        (_state.value as? BackupUi.Preview)?.let { manager.discard(it.prepared) }
        _state.value = BackupUi.Idle
    }

    fun dismiss() { _state.value = BackupUi.Idle }

    // ---- automatic backups (opt-in) ----------------------------------------------------------------------------------

    fun setAutoBackupEnabled(value: Boolean) = settings.setAutoBackupEnabled(value)

    fun setAutoBackupFolder(tree: Uri) {
        try {
            container.appContext.contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            _state.value = BackupUi.Done(R.string.backup_auto_folder_failed, true, e.message.orEmpty())
            return
        }
        settings.setAutoBackupFolder(tree.toString())
        // A new folder gets its first backup at the next opportunity.
        settings.lastAutoBackupAt = 0L
        settings.lastAutoBackupTryAt = 0L
        _lastFolderBackup.value = 0L
    }

    fun backupNow() {
        val folder = settings.autoBackupFolder.value ?: return
        if (_state.value is BackupUi.Working) return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = container.autoBackup.backupToFolder(folder)
            _lastFolderBackup.value = settings.lastAutoBackupAt
            _state.value = if (r.isSuccess) BackupUi.Done(R.string.backup_auto_ok, false)
            else BackupUi.Done(R.string.backup_auto_failed, true, r.exceptionOrNull()?.message.orEmpty())
        }
    }

    fun setAndroidCopy(value: Boolean) {
        settings.setAndroidBackupCopy(value)
        if (!value) {
            container.autoBackup.removeAndroidCopy()
            _lastAndroidCopy.value = 0L
            return
        }
        if (_state.value is BackupUi.Working) return
        _state.value = BackupUi.Working
        viewModelScope.launch {
            val r = container.autoBackup.writeAndroidCopy()
            _lastAndroidCopy.value = settings.lastAndroidCopyAt
            _state.value = if (r.isSuccess) BackupUi.Done(R.string.backup_android_ok, false)
            else BackupUi.Done(R.string.backup_android_failed, true, r.exceptionOrNull()?.message.orEmpty())
        }
    }

    override fun onCleared() {
        (_state.value as? BackupUi.Preview)?.let { manager.discard(it.prepared) }
    }
}
