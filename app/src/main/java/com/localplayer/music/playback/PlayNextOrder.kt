package com.localplayer.music.playback

/** Pure queue-position logic for "Play next" (no Android classes, so it is unit-tested on the JVM). */
object PlayNextOrder {
    /**
     * Index at which a new "Play next" batch goes: right after the current item and after the
     * contiguous run of items that were themselves queued with Play next.
     */
    fun insertIndex(currentIndex: Int, itemCount: Int, isPlayNext: (Int) -> Boolean): Int {
        var index = (currentIndex + 1).coerceAtLeast(0)
        while (index < itemCount && isPlayNext(index)) index++
        return index.coerceAtMost(itemCount)
    }
}
