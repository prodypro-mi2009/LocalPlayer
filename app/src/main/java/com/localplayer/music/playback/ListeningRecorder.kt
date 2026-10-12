package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.localplayer.music.data.stats.ListeningMeter
import com.localplayer.music.data.stats.StatsRepository
import com.localplayer.music.data.stats.TimeRanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Feeds [ListeningMeter] from the REAL Media3 player (it lives in the playback service, so it works with the
 * screen off or the app closed). Time counts only while `isPlaying` is true. It saves every [TICK_MS] while playing
 * and at every pause, track change, repeat and shutdown, so a killed service loses at most one interval.
 */
class ListeningRecorder(
    private val player: Player,
    private val repository: StatsRepository,
) : Player.Listener {

    // Not tied to the service scope: the last save at shutdown must still finish.
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val meter = ListeningMeter { uri, ms, plays ->
        val day = TimeRanges.dayKey(LocalDate.now())
        val now = System.currentTimeMillis()
        writer.launch { runCatching { repository.record(day, uri, ms, plays, now) } }
    }
    private val tick = object : Runnable {
        override fun run() {
            meter.tick(SystemClock.elapsedRealtime())
            if (meter.isRunning) handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        player.addListener(this)
        // The service may start with an item already loaded.
        player.currentMediaItem?.let { startItem(it) }
    }

    fun release() {
        handler.removeCallbacks(tick)
        player.removeListener(this)
        meter.release(SystemClock.elapsedRealtime())
    }

    private fun uriOf(item: MediaItem?): String? = item?.localConfiguration?.uri?.toString()

    private fun startItem(item: MediaItem?) {
        val duration = player.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
        meter.startItem(uriOf(item), duration, SystemClock.elapsedRealtime(), player.isPlaying)
        scheduleTick()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        if (meter.isRunning) handler.postDelayed(tick, TICK_MS)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = startItem(mediaItem)

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        meter.setPlaying(isPlaying, SystemClock.elapsedRealtime())
        scheduleTick()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            val d = player.duration
            if (d > 0L && d != androidx.media3.common.C.TIME_UNSET) meter.setDuration(d)
        }
    }

    private companion object {
        const val TICK_MS = 10_000L
    }
}
