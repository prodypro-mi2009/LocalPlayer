package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import com.localplayer.music.data.db.HistoryDao
import com.localplayer.music.data.db.HistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Records a listening-history entry once per meaningful play: after 30 seconds of actual playback
 * (or half of a track shorter than a minute). It uses one scheduled callback per track and no
 * polling, so it adds no periodic wake-ups, and writes at most one row per qualifying play.
 */
class HistoryRecorder(
    private val player: Player,
    private val dao: HistoryDao,
    private val scope: CoroutineScope,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var currentUri: String? = null
    private var playedMs = 0L
    private var playingSince = 0L
    private var recorded = false
    private val threshold = Runnable { onThresholdReached() }

    init {
        player.addListener(this)
    }

    fun release() {
        handler.removeCallbacks(threshold)
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        handler.removeCallbacks(threshold)
        currentUri = mediaItem?.localConfiguration?.uri?.toString()
        playedMs = 0L
        recorded = false
        if (player.isPlaying) startTimer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (currentUri == null) currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (isPlaying) startTimer() else pauseTimer()
    }

    private fun startTimer() {
        if (recorded || currentUri == null) return
        handler.removeCallbacks(threshold)
        playingSince = SystemClock.elapsedRealtime()
        handler.postDelayed(threshold, (requiredMs() - playedMs).coerceAtLeast(0L))
    }

    private fun pauseTimer() {
        if (playingSince != 0L) {
            playedMs += SystemClock.elapsedRealtime() - playingSince
            playingSince = 0L
        }
        handler.removeCallbacks(threshold)
    }

    private fun requiredMs(): Long {
        val duration = player.duration
        return if (duration > 0 && duration < 60_000L) duration / 2 else MIN_PLAY_MS
    }

    private fun onThresholdReached() {
        val uri = currentUri ?: return
        if (recorded) return
        recorded = true
        scope.launch {
            val now = System.currentTimeMillis()
            // Skip a second entry for the same track within a minute (seek-back loops, quick restarts).
            val last = dao.lastPlayedAt(uri)
            if (last == null || now - last > DEDUP_WINDOW_MS) {
                dao.insert(HistoryEntity(trackUri = uri, playedAt = now))
                dao.prune(MAX_ENTRIES)
            }
        }
    }

    private companion object {
        const val MIN_PLAY_MS = 30_000L
        const val DEDUP_WINDOW_MS = 60_000L
        const val MAX_ENTRIES = 3000
    }
}
