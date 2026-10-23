package com.localplayer.music.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.random.Random

data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: ArtworkRef?,
    val durationHintMs: Long,
    /** Stable track URI (favourites, lyrics, audio specs are looked up by it). */
    val uri: String?,
)

data class PlayerState(
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val durationMs: Long = 0L,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val shuffleEnabled: Boolean = false,
    /** One of Player.REPEAT_MODE_OFF / ONE / ALL. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

data class QueueEntry(
    val index: Int,
    val mediaId: String,
    /** Unique id of THIS entry of the queue (differs for two entries of the same file); null for legacy items. */
    val occurrenceId: String?,
    val title: String,
    val artist: String,
    val artwork: ArtworkRef?,
)

/**
 * UI-side handle to the playback service. Must be used from the main thread (calls from other threads are
 * posted to it, so the controller is only ever touched on its application thread).
 * The controller is connected while the UI is visible and released when it is not;
 * playback itself lives in [PlaybackService] and is unaffected.
 *
 * Connection lifecycle ([ControllerLifecycle]): commands issued before the controller is connected wait; if the
 * connection is released, replaced or fails they are CANCELLED (suspending callers get a CancellationException,
 * fire-and-forget commands are dropped), never left suspended and never run against a later controller.
 */
class PlayerConnection(private val context: Context) {

    private val lifecycle = ControllerLifecycle<MediaController>()
    private var future: ListenableFuture<MediaController>? = null
    private var lastPositionMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Which entries added to an empty queue were never played (first-item Undo); forgotten on release. */
    private val startTracker = StartTracker()

    /**
     * Counts the times the queue received songs (see [QueueEpoch]). A dismiss Undo is only valid while unchanged.
     */
    private val epoch = QueueEpoch()

    private val controller: MediaController? get() = lifecycle.current

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _positionJumps = MutableStateFlow(0)
    /** Increases on every seek / position jump, so a synchronized view can re-sync even while paused. */
    val positionJumps: StateFlow<Int> = _positionJumps.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) epoch.onPlaylistChanged(timeline.windowCount)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) _positionJumps.value++
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue(player)
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                ) && (player.isPlaying || player.playWhenReady)
            ) {
                markStarted(player)
            }
            publishState(player)
        }
    }

    private fun markStarted(player: Player) {
        startTracker.onPlaybackStarted(player.currentMediaItem?.let { MediaItemFactory.occurrenceId(it) })
    }

    fun connect() {
        runOnMain { connectOnMain() }
    }

    private fun connectOnMain() {
        val attempt = lifecycle.beginConnect()
        if (attempt < 0) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pendingFuture = MediaController.Builder(context, token).buildAsync()
        future = pendingFuture
        pendingFuture.addListener(
            Runnable {
                val connected = try {
                    pendingFuture.get()
                } catch (e: Exception) {
                    if (future === pendingFuture) future = null
                    lifecycle.failed(attempt) // waiting commands are cancelled; an older attempt is ignored
                    return@Runnable
                }
                val accepted = lifecycle.connected(attempt, connected) { c ->
                    c.addListener(listener)
                    publishQueue(c)
                    publishState(c)
                }
                if (!accepted) MediaController.releaseFuture(pendingFuture) // released or replaced meanwhile
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        runOnMain {
            controller?.let {
                lastPositionMs = it.currentPosition
                it.removeListener(listener)
            }
            lifecycle.release() // cancels every command still waiting
            future?.let { MediaController.releaseFuture(it) }
            future = null
            startTracker.clear()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun submit(command: Command<MediaController>) {
        if (!lifecycle.submit(command)) connectOnMain()
    }

    /** Fire-and-forget command: runs when connected, is dropped if the connection is abandoned first. */
    private fun withController(action: (MediaController) -> Unit) {
        runOnMain { submit(Command(onRun = { action(it) }, onCancel = {})) }
    }

    /**
     * Command with a result. Cancellation-safe: if the caller is cancelled the command is discarded and never
     * runs; if the connection is abandoned the caller is cancelled; the continuation is resumed exactly once.
     */
    private suspend fun <T> request(op: (MediaController) -> T): T = suspendCancellableCoroutine { cont ->
        val command = Command<MediaController>(
            onRun = { c ->
                if (cont.isActive) {
                    try {
                        cont.resume(op(c))
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                }
            },
            onCancel = { cont.cancel(CancellationException("Player connection released")) },
        )
        cont.invokeOnCancellation { runOnMain { lifecycle.discard(command) } }
        runOnMain { submit(command) }
    }

    // --- Commands ------------------------------------------------------------------------

    /**
     * Starts a new queue (tap a song, Play all, Shuffle all). Follows the Shuffle switch, see [QueueOps.replaceQueue];
     * [forceShuffle] (Shuffle all) turns the switch on, so the icon always matches the real order.
     */
    fun playQueue(items: List<MediaItem>, startIndex: Int, forceShuffle: Boolean = false) = withController {
        startTracker.clear()
        QueueOps.replaceQueue(it, items, startIndex, forceShuffle)
        it.prepare()
        it.play()
        markStarted(it)
    }

    fun togglePlayPause() = withController {
        if (it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
            it.play()
            markStarted(it) // recorded at command time: a play followed by an instant pause still counts as started
        }
    }

    /**
     * Shuffle on: the upcoming songs are reordered once (Play next songs stay first). Shuffle off: they go back
     * to the order they had before, unless the user reordered by hand meanwhile. Either way the queue shows, and
     * plays, one single order.
     */
    fun toggleShuffle() = withController {
        val turnOn = !it.shuffleModeEnabled
        QueueOps.reorderUpcoming(it, turnOn)
        it.shuffleModeEnabled = turnOn
    }

    /** off -> all -> one -> off */
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    /**
     * Jumps to a queued track. Tapping the track that is ALREADY current must not restart it: it only
     * resumes if it was paused.
     */
    fun playQueueIndex(index: Int) = withController {
        if (index !in 0 until it.mediaItemCount) return@withController
        if (index == it.currentMediaItemIndex) {
            if (!it.isPlaying && !it.playWhenReady) {
                it.play()
                markStarted(it)
            }
        } else {
            it.seekToDefaultPosition(index)
            it.prepare()
            it.play()
            markStarted(it)
        }
    }

    /** A queue entry that was taken out, so it can be put back at the same place (Undo). */
    data class RemovedItem(val index: Int, val item: MediaItem)

    /**
     * Removes ONE queue entry with `removeMediaItem` only: no prepare / stop / seek / playWhenReady change,
     * so the current track keeps playing without a gap when it is not the removed one.
     */
    suspend fun removeQueueItem(index: Int): RemovedItem? = request { c ->
        if (index !in 0 until c.mediaItemCount) {
            null
        } else {
            val item = MediaItemFactory.playable(c.getMediaItemAt(index))
            c.removeMediaItem(index)
            item?.let { RemovedItem(index, it) }
        }
    }

    /**
     * Removes the queue entry with this occurrence id (so a reorder that happened in the meantime can never make
     * the wrong row disappear). Same guarantees as [removeQueueItem]: only `removeMediaItem` is called.
     */
    suspend fun removeQueueEntry(occurrenceId: String): RemovedItem? = request { c ->
        val index = (0 until c.mediaItemCount).indexOfFirst { MediaItemFactory.occurrenceId(c.getMediaItemAt(it)) == occurrenceId }
        if (index < 0) {
            null
        } else {
            val item = MediaItemFactory.playable(c.getMediaItemAt(index))
            c.removeMediaItem(index)
            item?.let { RemovedItem(index, it) }
        }
    }

    /**
     * Moves the entry with this occurrence id to position [to] with ONE `moveMediaItem`. Media3 keeps the current
     * item playing (no restart, no seek) and adjusts the current index itself, also when the moved entry is the
     * playing one or passes it. With Shuffle on this also drops the shuffle ranks (see [QueueOps.moveEntry]).
     */
    fun moveQueueEntry(occurrenceId: String, to: Int) = withController { c ->
        QueueOps.moveEntry(c, occurrenceId, to)
    }

    /**
     * Removes every queue entry of one track (hiding a song). Highest index first, with `removeMediaItem` only; if
     * the playing entry is among them Media3 continues with the next entry (or ends when there is none).
     */
    suspend fun removeAllOfTrack(trackUri: String): Int = request { c ->
        var removed = 0
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (MediaItemFactory.trackUri(c.getMediaItemAt(i)) == trackUri) {
                c.removeMediaItem(i)
                removed++
            }
        }
        removed
    }

    /** Puts a removed entry back (pure insert; the current track is not touched). */
    fun reinsert(removed: RemovedItem) = withController {
        it.addMediaItem(removed.index.coerceIn(0, it.mediaItemCount), removed.item)
    }

    /**
     * Clear upcoming: removes what would play after the current track in ONE operation (with Repeat ALL also the
     * songs it would wrap to). The current track keeps playing untouched. Null when there was nothing to clear.
     */
    suspend fun clearUpcoming(): QueueOps.Cleared? = request { c -> QueueOps.clearUpcoming(c) }

    /**
     * Queue screen "Clear": keeps ONLY the current song, untouched (same item, position and play state; no stop,
     * no clearMediaItems, no setMediaItems). Null when there was nothing else to clear.
     */
    suspend fun clearQueueExceptCurrent(): QueueOps.Cleared? = request { c -> QueueOps.clearExceptCurrent(c) }

    /** Undo of either Clear; false (and nothing changed) when the queue is no longer what the Clear left. */
    suspend fun undoClearQueue(cleared: QueueOps.Cleared): Boolean = request { c -> QueueOps.undoClear(c, cleared) }

    fun moveQueueItem(from: Int, to: Int) = withController {
        val count = it.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) it.moveMediaItem(from, to)
    }

    // --- Editing the file of a queued track -------------------------------------------------

    /** State of the track whose file is about to be rewritten. */
    data class EditHandle(val wasCurrent: Boolean, val positionMs: Long, val wasPlaying: Boolean)

    /**
     * Call before the audio file is rewritten. If it is the current track it is paused, so the
     * player is not reading the file while its bytes change.
     */
    suspend fun beginFileEdit(mediaId: String): EditHandle? = request { c ->
        var handle: EditHandle? = null
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == mediaId) {
                val current = i == c.currentMediaItemIndex
                handle = EditHandle(
                    wasCurrent = current,
                    positionMs = if (current) c.currentPosition else 0L,
                    wasPlaying = current && (c.isPlaying || c.playWhenReady),
                )
                if (current) c.pause()
                break
            }
        }
        handle
    }

    /**
     * Call after the file was rewritten (or restored). Every queue entry of that track is replaced
     * with a fresh item (new tags, new source): the current one resumes at the same position, so
     * the only interruption is the one technically required because the file's bytes changed.
     */
    fun finishFileEdit(handle: EditHandle?, mediaId: String, newItem: MediaItem?) = withController { c ->
        if (newItem == null) {
            if (handle?.wasPlaying == true) c.play()
            return@withController
        }
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId != mediaId) continue
            val wasCurrent = i == c.currentMediaItemIndex
            val position = if (wasCurrent) (handle?.positionMs ?: c.currentPosition) else 0L
            c.removeMediaItem(i)
            c.addMediaItem(i, newItem)
            if (wasCurrent) {
                c.seekTo(i, position)
                c.prepare()
                if (handle?.wasPlaying == true) c.play()
            }
        }
    }

    // --- Mini-player dismiss / Undo ---------------------------------------------------------

    /**
     * The whole queue, current index and position, so the dismissal can be undone. [epoch] ties it to the queue
     * state right after the dismissal: the Undo is refused if the queue received songs since.
     */
    data class QueueSnapshot(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
        internal val epoch: Long,
    )

    /**
     * Mini-player dismiss: takes the snapshot and clears the queue in ONE controller operation (so nothing can
     * change between the two). Null when the queue was already empty.
     */
    suspend fun dismissQueue(): QueueSnapshot? = request { c ->
        if (c.mediaItemCount == 0) {
            null
        } else {
            val items = (0 until c.mediaItemCount).mapNotNull { MediaItemFactory.playable(c.getMediaItemAt(it)) }
            val index = c.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val position = c.currentPosition
            val wasPlaying = c.isPlaying || c.playWhenReady
            c.stop()
            c.clearMediaItems()
            startTracker.clear()
            QueueSnapshot(items, index, position, wasPlaying, epoch.value)
        }
    }

    /**
     * Undo of [dismissQueue]. Checked again here, on the controller thread, immediately before restoring: the
     * queue must still be empty AND must not have received any song since the dismissal (selecting a song,
     * adding one, replacing the queue: even if it was removed again). Otherwise nothing changes and false is
     * returned. A valid Undo restores queue, current song, position and the play/pause state.
     */
    suspend fun restoreQueue(snapshot: QueueSnapshot): Boolean = request { c ->
        if (!epoch.canRestoreDismissed(c.mediaItemCount, snapshot.epoch)) {
            false
        } else {
            c.setMediaItems(snapshot.items, snapshot.index, snapshot.positionMs)
            c.prepare()
            if (snapshot.wasPlaying) c.play() else c.pause()
            true
        }
    }

    /**
     * Appends ONE new occurrence of [item] to the end of the queue and returns its token. Pure `addMediaItem`:
     * the current item, position and play state are never touched. If nothing is queued yet the item is only
     * prepared (not played) so the gesture never starts playback by itself.
     */
    suspend fun appendOccurrence(item: MediaItem): QueueOps.Added = request { c ->
        QueueOps.appendOccurrence(c, item).also { if (it.becameCurrent) startTracker.onAddedToEmptyQueue(it.occurrenceId) }
    }

    /**
     * Undo of an add: removes exactly that entry with `removeMediaItem`. No setMediaItems / prepare / seekTo /
     * stop, so the playing track keeps playing from where it is. True when an entry was removed.
     */
    suspend fun removeOccurrence(added: QueueOps.Added): Boolean = request { c ->
        val removed = QueueOps.removeOccurrence(c, added.occurrenceId, startTracker.neverStarted(added.occurrenceId))
        if (removed) startTracker.forget(added.occurrenceId)
        removed
    }

    /**
     * Inserts ONE new occurrence of [item] as the next song (right after the current one; the newest Play next
     * plays first) and returns its token for a targeted Undo. Pure `addMediaItem`: the current item, position and
     * play state are never touched. If nothing is queued yet the item is only prepared (not played).
     */
    suspend fun insertNextOccurrence(item: MediaItem): QueueOps.Added = request { c ->
        QueueOps.insertNextOccurrence(c, item).also { if (it.becameCurrent) startTracker.onAddedToEmptyQueue(it.occurrenceId) }
    }

    // --- Position (polled by the UI only while it is visible) -----------------------------

    fun currentPositionMs(): Long = controller?.currentPosition ?: lastPositionMs


    // --- State publishing ----------------------------------------------------------------

    private fun publishState(player: Player) {
        val item = player.currentMediaItem
        val nowPlaying = item?.let {
            NowPlaying(
                mediaId = it.mediaId,
                title = it.mediaMetadata.title?.toString().orEmpty(),
                artist = it.mediaMetadata.artist?.toString().orEmpty(),
                album = it.mediaMetadata.albumTitle?.toString().orEmpty(),
                artwork = MediaItemFactory.artworkRef(it),
                durationHintMs = it.mediaMetadata.durationMs ?: 0L,
                uri = MediaItemFactory.trackUri(it),
            )
        }
        val playing = player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val reportedDuration = player.duration
        val duration = if (reportedDuration != C.TIME_UNSET && reportedDuration > 0) {
            reportedDuration
        } else {
            nowPlaying?.durationHintMs ?: 0L
        }
        // One atomic snapshot: title, artist, artwork and duration all come from the same MediaItem.
        _state.value = PlayerState(
            nowPlaying = nowPlaying,
            isPlaying = playing,
            hasNext = player.hasNextMediaItem(),
            durationMs = duration,
            queueIndex = player.currentMediaItemIndex,
            queueSize = player.mediaItemCount,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    private fun publishQueue(player: Player) {
        val count = player.mediaItemCount
        val entries = ArrayList<QueueEntry>(count)
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            entries.add(
                QueueEntry(
                    index = i,
                    mediaId = item.mediaId,
                    occurrenceId = MediaItemFactory.occurrenceId(item),
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    artwork = MediaItemFactory.artworkRef(item),
                ),
            )
        }
        _queue.value = entries
    }
}
