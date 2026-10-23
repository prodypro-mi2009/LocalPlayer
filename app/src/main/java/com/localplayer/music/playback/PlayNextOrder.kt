package com.localplayer.music.playback

/** Pure queue-position logic for "Play next" (no Android classes, so it is unit-tested on the JVM). */
object PlayNextOrder {
    /**
     * Policy: the newest Play next always goes IMMEDIATELY after the current item. So if E is queued with Play
     * next and then Z before the current track advances, Z plays before E (last in, first out). The same holds
     * with Shuffle on, because the queue order is the playback order.
     */
    fun insertIndex(currentIndex: Int, itemCount: Int): Int =
        (currentIndex + 1).coerceIn(0, itemCount.coerceAtLeast(0))
}
