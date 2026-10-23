package com.localplayer.music.playback.session

/** One queue entry as saved to disk: enough to rebuild the exact entry (duplicates keep their own occurrence id). */
data class SavedEntry(
    val occurrenceId: String,
    val trackUri: String,
    val playNext: Boolean,
    /** Queue position the song had when Shuffle was turned on, or -1. */
    val rank: Int,
)

/** The whole queue. [generation] ties a [SavedState] to the queue revision it was taken with. */
data class SavedQueue(val generation: Long, val entries: List<SavedEntry>)

/** Small, frequently rewritten part of the session. */
data class SavedState(
    val queueGeneration: Long,
    val currentOccurrenceId: String?,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val writtenAtMs: Long,
)

data class SavedSession(val queue: SavedQueue, val state: SavedState?)
