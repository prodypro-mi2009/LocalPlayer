package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextOrderTest {
    private fun playNext(queue: MutableList<String>, current: Int, vararg names: String) {
        queue.addAll(PlayNextOrder.insertIndex(current, queue.size), names.toList())
    }

    @Test fun newestPlayNextPlaysFirst() {
        val queue = mutableListOf("A", "B", "C")
        playNext(queue, 0, "E")
        playNext(queue, 0, "Z")
        assertEquals(listOf("A", "Z", "E", "B", "C"), queue) // Z before E
    }

    @Test fun threeCallsAreLastInFirstOut() {
        val queue = mutableListOf("A", "B")
        playNext(queue, 0, "D")
        playNext(queue, 0, "E")
        playNext(queue, 0, "F")
        assertEquals(listOf("A", "F", "E", "D", "B"), queue)
    }

    @Test fun afterAdvancingNewItemsStillGoRightAfterTheCurrentSong() {
        val queue = mutableListOf("A", "B")
        playNext(queue, 0, "E")
        playNext(queue, 0, "Z") // A Z E B
        playNext(queue, 1, "Q") // Z is playing now
        assertEquals(listOf("A", "Z", "Q", "E", "B"), queue)
    }

    @Test fun plainQueuedItemsAreNotSkipped() {
        val queue = mutableListOf("A", "B", "C")
        playNext(queue, 1, "D")
        assertEquals(listOf("A", "B", "D", "C"), queue)
    }

    @Test fun unsetCurrentIndexInsertsAtTheFront() = assertEquals(0, PlayNextOrder.insertIndex(-1, 3))

    @Test fun neverBeyondTheEnd() {
        assertEquals(2, PlayNextOrder.insertIndex(5, 2))
        assertEquals(0, PlayNextOrder.insertIndex(-1, 0))
    }
}
