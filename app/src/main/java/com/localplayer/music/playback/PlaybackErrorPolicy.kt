package com.localplayer.music.playback


/**
 * What to do when the player reports an error for the current queue entry. Pure (no Android classes needed at
 * run time except the annotation); the error codes are copied from Media3's `PlaybackException` and an
 * instrumented test checks that they still match.
 *
 *  - The FILE is at fault (missing, container malformed/unsupported, format unsupported): remove the entry and
 *    mark the track unavailable (hidden from the library until a rescan finds it again).
 *  - The entry cannot be played now but nothing proves the file is gone (no permission, read failure, decoder
 *    problem for this file, format beyond this device): remove the entry from the queue, do NOT mark the track.
 *  - Anything else (audio output / session failures, timeouts, unknown): not the track's fault. Keep the queue,
 *    pause. Pressing Play prepares again, which is the retry.
 *  - After [MAX_CONSECUTIVE_SKIPS] skips in a row without a single successful start, stop skipping and pause.
 */
object PlaybackErrorPolicy {
    const val MAX_CONSECUTIVE_SKIPS = 5

    sealed interface Decision {
        data class Skip(val markUnavailable: Boolean) : Decision
        object PauseKeep : Decision
    }

    internal const val IO_FILE_NOT_FOUND = 2005
    internal const val PARSING_CONTAINER_MALFORMED = 3001
    internal const val PARSING_CONTAINER_UNSUPPORTED = 3003
    internal const val DECODING_FORMAT_UNSUPPORTED = 4005

    private val FILE_AT_FAULT = setOf(
        IO_FILE_NOT_FOUND, PARSING_CONTAINER_MALFORMED, PARSING_CONTAINER_UNSUPPORTED, DECODING_FORMAT_UNSUPPORTED,
    )

    /** IO_UNSPECIFIED, IO_NO_PERMISSION, IO_READ_POSITION_OUT_OF_RANGE, manifest parsing, decoder init/query/failed/exceeds. */
    private val SKIP_ONLY = setOf(2000, 2006, 2008, 3002, 3004, 4001, 4002, 4003, 4004)

    fun decide(errorCode: Int, consecutiveSkips: Int): Decision = when {
        errorCode in FILE_AT_FAULT -> if (consecutiveSkips >= MAX_CONSECUTIVE_SKIPS) Decision.PauseKeep else Decision.Skip(true)
        errorCode in SKIP_ONLY -> if (consecutiveSkips >= MAX_CONSECUTIVE_SKIPS) Decision.PauseKeep else Decision.Skip(false)
        else -> Decision.PauseKeep
    }

    /**
     * After the failing entry was removed: pause (true) or carry on with the entry that took its place (false).
     * Repeat OFF / ONE: pause when the removed entry was the last one (nothing follows). Repeat ALL: the queue
     * wraps to its first entry, so carry on. An empty queue always pauses.
     */
    fun pauseAfterRemoval(removedWasLast: Boolean, remainingCount: Int, repeatMode: Int): Boolean = when {
        remainingCount <= 0 -> true
        removedWasLast -> repeatMode != REPEAT_MODE_ALL
        else -> false
    }

    private const val REPEAT_MODE_ALL = 2 // androidx.media3.common.Player.REPEAT_MODE_ALL
}
