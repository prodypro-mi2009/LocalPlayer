package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.localplayer.music.playback.session.SavedEntry
import com.localplayer.music.playback.session.SavedQueue
import com.localplayer.music.playback.session.SavedState
import com.localplayer.music.playback.session.SessionStoreCore

/**
 * Keeps the saved playback session in step with the live player. Lives in the playback service, on the main
 * thread; it only collects small in-memory data and hands it to [SessionStoreCore], which does all disk work on
 * its own background thread.
 *
 *  - queue changes rewrite the queue file (not on every progress/duration update of the timeline);
 *  - pause, seek, track change, shuffle and repeat changes checkpoint the state immediately;
 *  - while playing, the state is checkpointed every [CHECKPOINT_MS];
 *  - a queue that becomes empty by a user action (dismiss, sleep timer, removing the last song) deletes the
 *    saved session, so an old queue never comes back;
 *  - [freezeAndSave] (app removed from Recent Apps) saves FIRST and then ignores everything, so the clearing of
 *    the live queue that follows can neither delete nor overwrite the saved session.
 */
class PlaybackSessionKeeper(
    private val player: Player,
    private val store: SessionStoreCore,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val log: (String) -> Unit = {},
) : Player.Listener {

    var isFrozen = false
        private set

    /**
     * True once the queue was edited after this keeper started (a song picked, added, removed...). A restore of
     * the saved session must then never be applied, even if the queue happens to be empty again.
     */
    var queueTouched = false
        private set

    private var queueGeneration = 0L
    private var entries: List<SavedEntry> = emptyList()
    private var queueDirty = false
    private var tickerScheduled = false

    private val ticker = Runnable {
        tickerScheduled = false
        if (!isFrozen && player.isPlaying) {
            checkpointState()
            scheduleTicker()
        }
    }

    fun start() {
        player.addListener(this)
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        // Only real queue edits; SOURCE_UPDATE (durations becoming known) must not rewrite the whole queue.
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            queueDirty = true
            queueTouched = true
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (isFrozen) return
        if (queueDirty) {
            queueDirty = false
            onQueueChanged()
        } else if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) {
            checkpointState()
        }
        if (player.isPlaying) scheduleTicker() else cancelTicker()
    }

    /**
     * Saves the live queue and position, then stops reacting to the player. Call BEFORE any stop/clear of the
     * live queue. Waits (bounded, no disk I/O on this thread) until the background writer has finished, and
     * reports what is known: [SessionStoreCore.SaveOutcome.DURABLE] only when everything is confirmed on disk;
     * TIMED_OUT (writer still busy) and FAILED are logged and returned, never reported as a successful save.
     * Called only when the app is closed or the service stops, never periodically.
     */
    fun freezeAndSave(timeoutMs: Long): SessionStoreCore.SaveOutcome {
        if (!isFrozen) {
            if (player.mediaItemCount > 0) {
                val captured = captureEntries()
                if (captured.isNotEmpty()) {
                    if (captured != entries) {
                        entries = captured
                        queueGeneration++
                        store.writeQueue(SavedQueue(queueGeneration, captured))
                    }
                    checkpointState()
                }
            }
            isFrozen = true
            cancelTicker()
        }
        val outcome = store.flushBlocking(timeoutMs)
        if (outcome != SessionStoreCore.SaveOutcome.DURABLE) log("final save not confirmed: $outcome")
        return outcome
    }

    fun release() {
        cancelTicker()
        player.removeListener(this)
    }

    private fun onQueueChanged() {
        if (player.mediaItemCount == 0) {
            // Emptied by a user action: forget the saved session (nothing is written for an empty queue).
            entries = emptyList()
            store.invalidate()
            return
        }
        val captured = captureEntries()
        if (captured.isEmpty()) return
        if (captured != entries) {
            entries = captured
            queueGeneration++
            store.writeQueue(SavedQueue(queueGeneration, captured))
        }
        checkpointState()
    }

    private fun checkpointState() {
        // No saved queue yet (or it was just cleared): a state alone would describe nothing.
        if (player.mediaItemCount == 0 || entries.isEmpty()) return
        val current = player.currentMediaItem
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition.coerceAtLeast(0L)
        store.writeState(
            SavedState(
                queueGeneration = queueGeneration,
                currentOccurrenceId = current?.let(MediaItemFactory::occurrenceId),
                currentIndex = player.currentMediaItemIndex,
                positionMs = position,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                writtenAtMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun captureEntries(): List<SavedEntry> {
        val count = player.mediaItemCount
        val out = ArrayList<SavedEntry>(count)
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val uri = MediaItemFactory.trackUri(item) ?: continue
            val occurrence = MediaItemFactory.occurrenceId(item) ?: continue
            out += SavedEntry(occurrence, uri, MediaItemFactory.isPlayNext(item), MediaItemFactory.rank(item))
        }
        return out
    }

    private fun scheduleTicker() {
        if (tickerScheduled || isFrozen) return
        tickerScheduled = true
        handler.postDelayed(ticker, CHECKPOINT_MS)
    }

    private fun cancelTicker() {
        handler.removeCallbacks(ticker)
        tickerScheduled = false
    }

    companion object {
        const val CHECKPOINT_MS = 4_000L
    }
}
