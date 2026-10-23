package com.localplayer.music.playback

/**
 * Operations that invalidate an Undo token (pure; unit-tested on the JVM):
 *
 *  - Undo of a mini-player dismiss: any queue edit that gives the queue songs (select a song, add, Play next,
 *    replace the queue, service-side restore). Removing them again does NOT revive the Undo.
 *    Not invalidating: natural progress, seeking, pausing, Shuffle/Repeat changes (the queue is empty anyway).
 *  - Undo of Clear / Clear upcoming: any add, remove, move or replacement of queue entries (the occurrence ids
 *    of the queue differ from what Clear left). Not invalidating: natural progress of the current song,
 *    seeking, pausing, Shuffle/Repeat changes that do not reorder (Shuffle on/off with nothing upcoming).
 *  - Undo of an add: that occurrence leaving the queue, or (for a first item added to an empty queue) playback
 *    having started for it.
 */
class QueueEpoch {
    var value = 0L
        private set

    /** Call for every playlist change with the number of entries the queue has afterwards. */
    fun onPlaylistChanged(itemCountAfter: Int) {
        if (itemCountAfter > 0) value++
    }

    /** A dismiss Undo may restore only into a still-empty queue that never received songs since the dismissal. */
    fun canRestoreDismissed(itemCountNow: Int, tokenEpoch: Long): Boolean = itemCountNow == 0 && value == tokenEpoch
}

object UndoGuards {
    /** A Clear may be undone only while the queue is exactly what the Clear left behind. */
    fun clearUndoValid(queueAfterClear: List<String?>, queueNow: List<String?>): Boolean = queueAfterClear == queueNow
}
