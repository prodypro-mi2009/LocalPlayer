package com.localplayer.music.playback

import android.content.ComponentName
import android.content.Context
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
 * UI-side handle to the playback service. Must be used from the main thread.
 * The controller is connected while the UI is visible and released when it is not;
 * playback itself lives in [PlaybackService] and is unaffected.
 */
class PlayerConnection(private val context: Context) {

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var lastPositionMs = 0L

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _positionJumps = MutableStateFlow(0)
    /** Increases on every seek / position jump, so a synchronized view can re-sync even while paused. */
    val positionJumps: StateFlow<Int> = _positionJumps.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) _positionJumps.value++
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue(player)
            publishState(player)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pendingFuture = MediaController.Builder(context, token).buildAsync()
        future = pendingFuture
        pendingFuture.addListener(
            Runnable {
                val connected = try {
                    pendingFuture.get()
                } catch (e: Exception) {
                    if (future === pendingFuture) future = null
                    return@Runnable
                }
                if (future !== pendingFuture) {
                    MediaController.releaseFuture(pendingFuture)
                    return@Runnable
                }
                controller = connected
                connected.addListener(listener)
                publishQueue(connected)
                publishState(connected)
                val actions = pending.toList()
                pending.clear()
                actions.forEach { it(connected) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controller?.let {
            lastPositionMs = it.currentPosition
            it.removeListener(listener)
        }
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
        } else {
            pending.add(action)
            connect()
        }
    }

    // --- Commands ------------------------------------------------------------------------

    fun playQueue(items: List<MediaItem>, startIndex: Int) = withController {
        it.setMediaItems(items, startIndex, 0L)
        it.prepare()
        it.play()
    }

    fun togglePlayPause() = withController {
        if (it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
            it.play()
        }
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

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
            if (!it.isPlaying && !it.playWhenReady) it.play()
        } else {
            it.seekToDefaultPosition(index)
            it.prepare()
            it.play()
        }
    }

    /** A queue entry that was taken out, so it can be put back at the same place (Undo). */
    data class RemovedItem(val index: Int, val item: MediaItem)

    /**
     * Removes ONE queue entry with `removeMediaItem` only: no prepare / stop / seek / playWhenReady change,
     * so the current track keeps playing without a gap when it is not the removed one.
     */
    suspend fun removeQueueItem(index: Int): RemovedItem? = suspendCoroutine { continuation ->
        withController { c ->
            if (index !in 0 until c.mediaItemCount) {
                continuation.resume(null)
            } else {
                val item = MediaItemFactory.playable(c.getMediaItemAt(index))
                c.removeMediaItem(index)
                continuation.resume(item?.let { RemovedItem(index, it) })
            }
        }
    }

    /** Puts a removed entry back (pure insert; the current track is not touched). */
    fun reinsert(removed: RemovedItem) = withController {
        it.addMediaItem(removed.index.coerceIn(0, it.mediaItemCount), removed.item)
    }

    /**
     * Removes everything after the current track in ONE operation. The current track keeps playing
     * untouched. Returns what was removed (for Undo) or an empty list.
     */
    suspend fun clearUpcoming(): List<RemovedItem> = suspendCoroutine { continuation ->
        withController { c ->
            val first = c.currentMediaItemIndex + 1
            val count = c.mediaItemCount
            if (c.currentMediaItemIndex < 0 || first >= count) {
                continuation.resume(emptyList())
            } else {
                val removed = (first until count).mapNotNull { i ->
                    MediaItemFactory.playable(c.getMediaItemAt(i))?.let { RemovedItem(i, it) }
                }
                c.removeMediaItems(first, count)
                continuation.resume(removed)
            }
        }
    }

    /** Puts the entries removed by [clearUpcoming] back at the end, in their original order. */
    fun reinsertAll(removed: List<RemovedItem>) = withController {
        if (removed.isNotEmpty()) it.addMediaItems(removed.map(RemovedItem::item))
    }

    fun moveQueueItem(from: Int, to: Int) = withController {
        val count = it.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) it.moveMediaItem(from, to)
    }

    /** Appends to the end of the queue (starts playing if nothing is queued yet). */
    fun addToQueue(items: List<MediaItem>) = withController {
        if (it.mediaItemCount == 0) {
            it.setMediaItems(items)
            it.prepare()
            it.play()
        } else {
            it.addMediaItems(items)
        }
    }

    /**
     * Inserts right after the current track AND after everything already queued with Play next, so
     * "Play next D", "Play next E" on current A gives A, D, E (not A, E, D). Starts playing if
     * nothing is queued yet.
     */
    fun playNext(items: List<MediaItem>) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
            c.play()
        } else {
            val at = PlayNextOrder.insertIndex(c.currentMediaItemIndex, c.mediaItemCount) { i ->
                MediaItemFactory.isPlayNext(c.getMediaItemAt(i))
            }
            c.addMediaItems(at, items.map(MediaItemFactory::markPlayNext))
        }
    }

    // --- Editing the file of a queued track -------------------------------------------------

    /** State of the track whose file is about to be rewritten. */
    data class EditHandle(val wasCurrent: Boolean, val positionMs: Long, val wasPlaying: Boolean)

    /**
     * Call before the audio file is rewritten. If it is the current track it is paused, so the
     * player is not reading the file while its bytes change.
     */
    suspend fun beginFileEdit(mediaId: String): EditHandle? = suspendCoroutine { continuation ->
        withController { c ->
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
            continuation.resume(handle)
        }
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

    // --- Queue snapshot / undo ---------------------------------------------------------------

    /** The whole queue, the current index and position, so a destructive action can be undone. */
    data class QueueSnapshot(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
    )

    /** Null when the queue is empty. */
    suspend fun snapshot(): QueueSnapshot? = suspendCoroutine { continuation ->
        withController { c ->
            if (c.mediaItemCount == 0) {
                continuation.resume(null)
            } else {
                val items = (0 until c.mediaItemCount).mapNotNull { MediaItemFactory.playable(c.getMediaItemAt(it)) }
                continuation.resume(
                    QueueSnapshot(
                        items = items,
                        index = c.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                        positionMs = c.currentPosition,
                        wasPlaying = c.isPlaying || c.playWhenReady,
                    ),
                )
            }
        }
    }

    fun restore(snapshot: QueueSnapshot) = withController {
        it.setMediaItems(snapshot.items, snapshot.index, snapshot.positionMs)
        it.prepare()
        if (snapshot.wasPlaying) it.play() else it.pause()
    }

    /**
     * Appends ONE new occurrence of [item] to the end of the queue and returns its occurrence id.
     * Pure `addMediaItem`: the current item, position and play state are never touched. If nothing is queued
     * yet the item is only prepared (not played) so the gesture never starts playback by itself.
     */
    suspend fun appendOccurrence(item: MediaItem): String = suspendCoroutine { continuation ->
        withController { c ->
            val id = QueueOccurrence.newId()
            val tagged = MediaItemFactory.withOccurrence(item, id)
            if (c.mediaItemCount == 0) {
                c.setMediaItems(listOf(tagged))
                c.prepare()
            } else {
                c.addMediaItem(tagged)
            }
            continuation.resume(id)
        }
    }

    /**
     * Undo of [appendOccurrence]: removes exactly that entry with `removeMediaItem`. No setMediaItems / prepare /
     * seekTo / stop, so the playing track keeps playing from where it is. Returns true when an entry was removed.
     */
    suspend fun removeOccurrence(occurrenceId: String): Boolean = suspendCoroutine { continuation ->
        withController { c ->
            val ids = (0 until c.mediaItemCount).map { MediaItemFactory.occurrenceId(c.getMediaItemAt(it)) }
            when (val plan = QueueOccurrence.planUndo(ids, c.currentMediaItemIndex, occurrenceId)) {
                is QueueOccurrence.UndoPlan.Remove -> { c.removeMediaItem(plan.index); continuation.resume(true) }
                else -> continuation.resume(false)
            }
        }
    }

    /**
     * Inserts ONE new occurrence of [item] as the next song (right after the current one, after earlier "Play next"
     * entries so repeated swipes keep their order) and returns its occurrence id for a targeted Undo.
     * Pure `addMediaItem`: the current item, position and play state are never touched. If nothing is queued
     * yet the item is only prepared (not played), like [appendOccurrence].
     */
    suspend fun insertNextOccurrence(item: MediaItem): String = suspendCoroutine { continuation ->
        withController { c ->
            val id = QueueOccurrence.newId()
            val tagged = MediaItemFactory.markPlayNext(MediaItemFactory.withOccurrence(item, id))
            if (c.mediaItemCount == 0) {
                c.setMediaItems(listOf(tagged))
                c.prepare()
            } else {
                val at = PlayNextOrder.insertIndex(c.currentMediaItemIndex, c.mediaItemCount) { i ->
                    MediaItemFactory.isPlayNext(c.getMediaItemAt(i))
                }
                c.addMediaItem(at, tagged)
            }
            continuation.resume(id)
        }
    }

    fun clearQueue() = withController {
        it.stop()
        it.clearMediaItems()
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
