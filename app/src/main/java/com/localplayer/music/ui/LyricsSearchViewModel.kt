package com.localplayer.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localplayer.music.AppContainer
import com.localplayer.music.data.lyrics.LrcLibClient
import com.localplayer.music.data.lyrics.LrcLibMatcher
import com.localplayer.music.data.lyrics.RankedLyrics
import com.localplayer.music.data.update.AppInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Internet Search screen shows. The search only ever starts from the user's button (the screen is opened by it). */
sealed interface LyricsSearchPhase {
    data object Loading : LyricsSearchPhase
    data class Results(val items: List<RankedLyrics>) : LyricsSearchPhase
    data object NoResults : LyricsSearchPhase
    data object OnlyInstrumental : LyricsSearchPhase
    data object NoTrack : LyricsSearchPhase
    data object Offline : LyricsSearchPhase
    data object RateLimited : LyricsSearchPhase
    data class ServerError(val code: Int) : LyricsSearchPhase
}

data class LyricsSearchState(
    val title: String = "",
    val artist: String = "",
    val phase: LyricsSearchPhase = LyricsSearchPhase.Loading,
    val selected: Int = 0,
    /** true = show / save the synchronized (LRC) text when the result has it. */
    val preferSynced: Boolean = true,
) {
    val current: RankedLyrics? get() = (phase as? LyricsSearchPhase.Results)?.items?.getOrNull(selected)

    /** The exact text that "Save to file" will write: synced when chosen and available, otherwise plain, otherwise synced. */
    val textToSave: String?
        get() {
            val r = current?.result ?: return null
            return when {
                preferSynced && r.hasSynced -> r.syncedLyrics
                r.hasPlain -> r.plainLyrics
                r.hasSynced -> r.syncedLyrics
                else -> null
            }
        }

    val savingSynced: Boolean
        get() {
            val r = current?.result ?: return false
            return textToSave != null && textToSave == r.syncedLyrics && r.hasSynced
        }
}

class LyricsSearchViewModel(private val container: AppContainer, private val trackId: Long) : ViewModel() {
    private val client = LrcLibClient(AppInfo.userAgent(container.appContext))
    private val _state = MutableStateFlow(LyricsSearchState())
    val state: StateFlow<LyricsSearchState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        search()
    }

    fun search() {
        job?.cancel()
        _state.update { it.copy(phase = LyricsSearchPhase.Loading, selected = 0) }
        job = viewModelScope.launch {
            val track = container.library.trackItem(trackId)
            if (track == null) {
                _state.update { it.copy(phase = LyricsSearchPhase.NoTrack) }
                return@launch
            }
            _state.update { it.copy(title = track.title, artist = track.primaryArtist ?: track.artist.orEmpty()) }
            val artist = track.primaryArtist ?: track.artist
            val phase = when (val outcome = client.search(track.title, artist, track.album, track.durationMs)) {
                is LrcLibClient.Outcome.Found -> {
                    val ranked = LrcLibMatcher.rank(outcome.results, track.title, artist, track.durationMs).take(MAX_SHOWN)
                    when {
                        ranked.isNotEmpty() -> LyricsSearchPhase.Results(ranked)
                        LrcLibMatcher.onlyInstrumental(outcome.results) -> LyricsSearchPhase.OnlyInstrumental
                        else -> LyricsSearchPhase.NoResults
                    }
                }
                LrcLibClient.Outcome.NetworkError -> LyricsSearchPhase.Offline
                LrcLibClient.Outcome.RateLimited -> LyricsSearchPhase.RateLimited
                is LrcLibClient.Outcome.ServerError -> LyricsSearchPhase.ServerError(outcome.code)
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = index) }
    fun setPreferSynced(value: Boolean) = _state.update { it.copy(preferSynced = value) }

    private companion object {
        const val MAX_SHOWN = 15
    }
}
