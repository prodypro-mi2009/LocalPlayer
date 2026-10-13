package com.localplayer.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.data.update.AppInfo
import com.localplayer.music.data.update.DownloadResult
import com.localplayer.music.data.update.ReleaseInfo
import com.localplayer.music.data.update.UpdateCheck
import com.localplayer.music.data.update.UpdateChecker
import com.localplayer.music.data.update.VersionCompare
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: String) : UpdateUi
    data class Available(val release: ReleaseInfo) : UpdateUi
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateUi
    data class Downloaded(val release: ReleaseInfo, val file: File) : UpdateUi
    data object NotConfigured : UpdateUi
    data object NoRelease : UpdateUi
    data object Offline : UpdateUi
    data object RateLimited : UpdateUi
    data class Failed(val code: Int) : UpdateUi
    data class DownloadFailed(val release: ReleaseInfo, val corrupt: Boolean) : UpdateUi
}

/**
 * Manual update flow of the About screen: check -> show version and notes -> download the release APK -> hand it to
 * Android's package installer (which asks the user to confirm). It never installs silently and runs nothing itself.
 */
class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val context = container.appContext
    private val checker = UpdateChecker(AppInfo.userAgent(context))
    private val _state = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val state: StateFlow<UpdateUi> = _state.asStateFlow()
    private var job: Job? = null

    fun check() {
        if (_state.value is UpdateUi.Checking || _state.value is UpdateUi.Downloading) return
        _state.value = UpdateUi.Checking
        job = viewModelScope.launch {
            val current = AppInfo.versionName(context)
            val result = checker.check(AppInfo.repo(context), current)
            if (result !is UpdateCheck.Offline) container.settings.lastUpdateCheckAt = System.currentTimeMillis()
            _state.value = when (result) {
                is UpdateCheck.Newer -> {
                    container.settings.knownNewerVersion = result.release.tag
                    UpdateUi.Available(result.release)
                }
                is UpdateCheck.UpToDate -> {
                    container.settings.knownNewerVersion = ""
                    UpdateUi.UpToDate(result.latestTag)
                }
                UpdateCheck.NotConfigured -> UpdateUi.NotConfigured
                UpdateCheck.NoRelease -> UpdateUi.NoRelease
                UpdateCheck.Offline -> UpdateUi.Offline
                UpdateCheck.RateLimited -> UpdateUi.RateLimited
                is UpdateCheck.Failed -> UpdateUi.Failed(result.code)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (_state.value is UpdateUi.Downloading) return
        _state.value = UpdateUi.Downloading(release, 0f)
        job = viewModelScope.launch {
            val dir = File(context.cacheDir, "updates")
            dir.listFiles()?.forEach { it.delete() }      // only the newest download is kept
            val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(dir, "LocalPlayer-$safeTag.apk")
            val result = checker.download(release, target) { p ->
                _state.value = UpdateUi.Downloading(release, p)
            }
            _state.value = when (result) {
                is DownloadResult.Done -> UpdateUi.Downloaded(release, result.file)
                DownloadResult.Corrupt -> UpdateUi.DownloadFailed(release, corrupt = true)
                else -> UpdateUi.DownloadFailed(release, corrupt = false)
            }
        }
    }

    /** Back to the "update available" state after a failed download so the user can retry. */
    fun backToAvailable(release: ReleaseInfo) {
        _state.value = UpdateUi.Available(release)
    }

    /** The newer version found by an earlier automatic check, if it is still newer than the installed one. */
    fun knownNewer(): String? =
        container.settings.knownNewerVersion.takeIf { it.isNotEmpty() && VersionCompare.isNewer(it, AppInfo.versionName(context)) }
}
