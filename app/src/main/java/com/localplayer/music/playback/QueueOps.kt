package com.localplayer.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.random.Random

/**
 * Queue operations on a Media3 [Player]. They work on a MediaController (the app) and on an ExoPlayer
 * (instrumented tests) alike.
 *
 * INVARIANT: the queue order IS the playback order (the service installs an unshuffled Media3 order); the
 * Shuffle switch is applied to the real queue, see [ShuffleQueue] for the full policy.
 *
 * UNDO TOKENS. Every undoable operation hands out a token that describes the exact queue state its Undo belongs
 * to; the Undo re-checks that state on the controller thread right before it changes anything and otherwise
 * fails safely (returns false, the UI says so):
 *  - add / Play next ([Added]): valid while that occurrence is still in the queue (and, if it is the current
 *    one, until playback was ever started for it);
 *  - Clear / Clear upcoming ([Cleared]): valid only while the queue is exactly what the operation left behind
 *    (same occurrence ids in the same order). ANY add, remove, reorder or replacement invalidates it;
 *    natural progress of the current song does not;
 *  - mini-player dismiss (PlayerConnection.QueueSnapshot): valid only while the queue is still empty and has not
 *    received any songs since the dismissal (a later add that was removed again also invalidates it).
 */
object QueueOps {
    data class Removed(val index: Int, val item: MediaItem)

    /** [becameCurrent] = the queue was empty, so the new entry was selected (prepared, not played). */
    data class Added(val occurrenceId: String, val becameCurrent: Boolean)

    /**
     * What a Clear removed, for Undo. [queueAfter] = occurrence ids of the queue it left behind: the token.
     * [before] is only non-empty when songs before the current one were removed too.
     */
    data class Cleared(val before: List<MediaItem>, val after: List<MediaItem>, val queueAfter: List<String?>)

    fun occurrenceIds(p: Player): List<String?> =
        (0 until p.mediaItemCount).map { MediaItemFactory.occurrenceId(p.getMediaItemAt(it)) }

    fun appendOccurrence(p: Player, item: MediaItem): Added {
        val id = QueueOccurrence.newId()
        val tagged = MediaItemFactory.withOccurrence(item, id)
        val wasEmpty = p.mediaItemCount == 0
        if (wasEmpty) {
            p.setMediaItems(listOf(tagged))
            p.prepare()
        } else {
            p.addMediaItem(tagged)
        }
        return Added(id, wasEmpty)
    }

    /** Play next: the newest one goes right after the current song (see [PlayNextOrder]). */
    fun insertNextOccurrence(p: Player, item: MediaItem): Added {
        val id = QueueOccurrence.newId()
        val tagged = MediaItemFactory.markPlayNext(MediaItemFactory.withOccurrence(item, id))
        val wasEmpty = p.mediaItemCount == 0
        if (wasEmpty) {
            p.setMediaItems(listOf(tagged))
            p.prepare()
        } else {
            p.addMediaItem(PlayNextOrder.insertIndex(p.currentMediaItemIndex, p.mediaItemCount), tagged)
        }
        return Added(id, wasEmpty)
    }

    /**
     * Starts a new queue. Honors the Shuffle switch: with it on (or [forceShuffle], which turns it on) the started
     * song stays current at position 0 and every other song follows in a random order, each remembering its
     * list position as its rank. Stale Play next flags and ranks are stripped, so nothing leaks from an old queue.
     * Callers prepare/play afterwards.
     */
    fun replaceQueue(p: Player, items: List<MediaItem>, startIndex: Int, forceShuffle: Boolean, random: Random = Random.Default) {
        if (items.isEmpty()) return
        val shuffle = forceShuffle || p.shuffleModeEnabled
        val clean = items.map { MediaItemFactory.clean(it) }
        val start = startIndex.coerceIn(0, clean.size - 1)
        if (shuffle) {
            val ordered = ShuffleQueue.replacementOrder(clean.size, start, random).map { MediaItemFactory.withRank(clean[it], it) }
            p.setMediaItems(ordered, 0, 0L)
        } else {
            p.setMediaItems(clean, start, 0L)
        }
        if (p.shuffleModeEnabled != shuffle) p.shuffleModeEnabled = shuffle
    }

    /**
     * Undo of an add: removes exactly that occurrence. Returns false when it cannot be undone (already gone, or
     * the user is listening to it). [neverStarted] must come from [StartTracker] (occurrence-specific history),
     * not from the position: an entry added to an empty queue is removable while playback was never started.
     */
    fun removeOccurrence(p: Player, occurrenceId: String, neverStarted: Boolean): Boolean {
        val notStarted = neverStarted && !p.isPlaying && !p.playWhenReady
        return when (val plan = QueueOccurrence.planUndo(occurrenceIds(p), p.currentMediaItemIndex, occurrenceId, notStarted)) {
            is QueueOccurrence.UndoPlan.Remove -> {
                p.removeMediaItem(plan.index)
                if (p.mediaItemCount == 0) p.stop()
                true
            }
            else -> false
        }
    }

    /**
     * Moves one entry. With Shuffle on this is a deliberate user ordering: the ranks of the upcoming songs are
     * dropped, so a later Shuffle off keeps this order instead of undoing it.
     */
    fun moveEntry(p: Player, occurrenceId: String, to: Int) {
        val from = (0 until p.mediaItemCount).firstOrNull { MediaItemFactory.occurrenceId(p.getMediaItemAt(it)) == occurrenceId }
            ?: return
        if (to !in 0 until p.mediaItemCount || to == from) return
        p.moveMediaItem(from, to)
        if (p.shuffleModeEnabled) dropUpcomingRanks(p)
    }

    private fun dropUpcomingRanks(p: Player) {
        val first = p.currentMediaItemIndex + 1
        val count = p.mediaItemCount
        if (p.currentMediaItemIndex < 0 || first >= count) return
        val items = (first until count).map { p.getMediaItemAt(it) }
        if (items.none { MediaItemFactory.rank(it) >= 0 }) return
        val plain = items.map { MediaItemFactory.playable(it) }
        if (plain.any { it == null }) return
        p.replaceMediaItems(first, count, plain.map { MediaItemFactory.withoutRank(it!!) })
    }

    /**
     * Clear upcoming: removes every song that would play after the current one, in ONE removal; the current song
     * is never touched. With Repeat ALL the songs before the current one are upcoming as well (the queue wraps
     * to them), so they are removed too; with Repeat OFF / ONE they are history and stay. Null if nothing to remove.
     */
    fun clearUpcoming(p: Player): Cleared? = clearAround(p, includeBefore = p.repeatMode == Player.REPEAT_MODE_ALL)

    /**
     * Queue screen "Clear": removes every song EXCEPT the current one. The current song keeps its media item,
     * position and play state (two range removals; no stop / clearMediaItems / setMediaItems / prepare).
     */
    fun clearExceptCurrent(p: Player): Cleared? = clearAround(p, includeBefore = true)

    private fun clearAround(p: Player, includeBefore: Boolean): Cleared? {
        val current = p.currentMediaItemIndex
        val count = p.mediaItemCount
        if (current < 0 || current >= count) return null
        val before = if (includeBefore) (0 until current).mapNotNull { MediaItemFactory.playable(p.getMediaItemAt(it)) } else emptyList()
        val after = (current + 1 until count).mapNotNull { MediaItemFactory.playable(p.getMediaItemAt(it)) }
        if (before.isEmpty() && after.isEmpty()) return null
        if (current + 1 < count) p.removeMediaItems(current + 1, count) // tail first: indices before it stay valid
        if (includeBefore && current > 0) p.removeMediaItems(0, current)
        return Cleared(before, after, occurrenceIds(p))
    }

    /** Undo of a Clear. False (nothing changed) unless the queue is exactly what the Clear left behind. */
    fun undoClear(p: Player, cleared: Cleared): Boolean {
        if (!UndoGuards.clearUndoValid(cleared.queueAfter, occurrenceIds(p))) return false
        if (cleared.before.isNotEmpty()) p.addMediaItems(0, cleared.before)
        if (cleared.after.isNotEmpty()) p.addMediaItems(cleared.after)
        return true
    }

    /**
     * Applies the Shuffle switch to the upcoming songs with ONE `replaceMediaItems`; the current song and
     * everything before it are untouched. Returns false when there was nothing to reorder.
     */
    fun reorderUpcoming(p: Player, shuffleOn: Boolean, random: Random = Random.Default): Boolean {
        val current = p.currentMediaItemIndex
        val count = p.mediaItemCount
        val first = current + 1
        if (current < 0 || count - first < 2) return false
        val items = (first until count).map { p.getMediaItemAt(it) }
        val playable = items.map { MediaItemFactory.playable(it) }
        if (playable.any { it == null }) return false
        val slots = items.map { ShuffleQueue.Slot(MediaItemFactory.isPlayNext(it), MediaItemFactory.rank(it)) }
        val currentRank = p.currentMediaItem?.let { MediaItemFactory.rank(it) } ?: -1
        val order = if (shuffleOn) ShuffleQueue.shuffledOrder(slots, random) else ShuffleQueue.restoredOrder(slots, currentRank)
        val reordered = order.map { i ->
            val base = playable[i]!!
            if (shuffleOn) MediaItemFactory.withRank(base, first + i) else MediaItemFactory.withoutRank(base)
        }
        p.replaceMediaItems(first, count, reordered)
        return true
    }
}
