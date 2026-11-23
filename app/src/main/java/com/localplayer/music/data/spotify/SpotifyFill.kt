package com.localplayer.music.data.spotify

import android.content.Context
import com.localplayer.music.data.download.DownloadItem
import com.localplayer.music.data.download.DownloadManager
import com.localplayer.music.data.download.YtDlpEngine
import kotlinx.coroutines.CancellationException
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

enum class FillPhase { IDLE, SEARCHING, DONE }

/** Why a run ended early (null = it ran to the end). */
enum class FillProblem { BOT, ENGINE, BUSY }

data class FillState(
    val phase: FillPhase = FillPhase.IDLE,
    val total: Int = 0,
    /** Songs searched for so far. */
    val searched: Int = 0,
    /** Songs for which YouTube has the same title, artist and version (they wait in the Download screen). */
    val found: Int = 0,
    /** Songs that YouTube does not have with the same title, artist and version. */
    val notFound: List<SpotifyTrack> = emptyList(),
    val problem: FillProblem? = null,
    /** yt-dlp's own words for [problem] (selectable on screen). */
    val detail: String? = null,
)

/**
 * "Find the missing songs on YouTube": for every Spotify song the library lacks it searches YouTube and accepts a
 * result only when title, artist and version really are the same. The accepted ones are put into the list of the
 * Download screen, where the user chooses the folder, the genre and the other settings and presses Download.
 * Nothing is downloaded here. Only runs when the user presses the button.
 */
class SpotifyFill(
    private val context: Context,
    private val downloads: DownloadManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(FillState())
    val state: StateFlow<FillState> = _state.asStateFlow()
    private var job: Job? = null

    val running: Boolean get() = _state.value.phase == FillPhase.SEARCHING

    fun clear() {
        if (!running) _state.value = FillState()
    }

    fun stop() {
        if (!running) return
        job?.cancel()
        _state.update { it.copy(phase = FillPhase.DONE) }
    }

    /** [missing] = the songs of the Spotify list that the library lacks. */
    fun start(missing: List<SpotifyTrack>) {
        if (running || missing.isEmpty()) return
        if (downloads.isBusy) {
            _state.value = FillState(phase = FillPhase.DONE, problem = FillProblem.BUSY)
            return
        }
        _state.value = FillState(phase = FillPhase.SEARCHING, total = missing.size)
        job = scope.launch {
            try {
                run(missing)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(phase = FillPhase.DONE, problem = FillProblem.ENGINE, detail = YtDlpEngine.describe(t)) }
            }
        }
    }

    private suspend fun run(missing: List<SpotifyTrack>) {
        val picked = LinkedHashMap<String, SpotifyTrack>() // video id -> song
        val notFound = ArrayList<SpotifyTrack>()
        for (track in missing) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val query = "${track.artist.split(',', '&').first().trim()} ${track.title}".trim()
            val hits = try {
                YtDlpEngine.search(context, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (YtDlpEngine.classify(e) == YtDlpEngine.FailureKind.BOT) {
                    _state.update { it.copy(phase = FillPhase.DONE, problem = FillProblem.BOT, detail = YtDlpEngine.describe(e)) }
                    return
                }
                emptyList()
            }
            val hit = YoutubeMatcher.best(track, hits)
            if (hit == null || hit.id in picked) notFound += track else picked[hit.id] = track
            _state.update { it.copy(searched = it.searched + 1, found = picked.size, notFound = notFound.toList()) }
        }
        if (picked.isNotEmpty()) {
            val items = picked.map { (id, track) -> DownloadItem(id = id, title = track.title, artist = track.artist) }
            if (!downloads.loadItems(items)) {
                _state.update { it.copy(phase = FillPhase.DONE, problem = FillProblem.BUSY) }
                return
            }
        }
        _state.update { it.copy(phase = FillPhase.DONE) }
    }
}
