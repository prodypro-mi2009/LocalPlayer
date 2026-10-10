package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextOrderTest {
    private data class Q(val name: String, val playNext: Boolean = false)

    private fun playNext(queue: MutableList<Q>, current: Int, vararg names: String) {
        val at = PlayNextOrder.insertIndex(current, queue.size) { queue[it].playNext }
        queue.addAll(at, names.map { Q(it, true) })
    }

    private fun names(queue: List<Q>) = queue.map { it.name }

    @Test
    fun repeatedPlayNextKeepsCallOrder() {
        val queue = mutableListOf(Q("A"), Q("B"), Q("C"))
        playNext(queue, 0, "D")
        playNext(queue, 0, "E")
        assertEquals(listOf("A", "D", "E", "B", "C"), names(queue))
    }

    @Test
    fun threeCallsStayInOrder() {
        val queue = mutableListOf(Q("A"), Q("B"))
        playNext(queue, 0, "D")
        playNext(queue, 0, "E")
        playNext(queue, 0, "F")
        assertEquals(listOf("A", "D", "E", "F", "B"), names(queue))
    }

    @Test
    fun batchKeepsInternalOrder() {
        val queue = mutableListOf(Q("A"), Q("B"))
        playNext(queue, 0, "D", "E")
        playNext(queue, 0, "F")
        assertEquals(listOf("A", "D", "E", "F", "B"), names(queue))
    }

    @Test
    fun plainQueuedItemsAreNotSkipped() {
        val queue = mutableListOf(Q("A"), Q("B"), Q("C"))
        playNext(queue, 1, "D")
        assertEquals(listOf("A", "B", "D", "C"), names(queue))
    }

    @Test
    fun afterAdvancingIntoTheBlockNewItemsGoAfterRemainingPlayNextItems() {
        val queue = mutableListOf(Q("A"), Q("B"))
        playNext(queue, 0, "D")
        playNext(queue, 0, "E")
        // D is now playing (index 1); E is still queued from Play next.
        playNext(queue, 1, "F")
        assertEquals(listOf("A", "D", "E", "F", "B"), names(queue))
    }

    @Test
    fun unsetCurrentIndexInsertsAtTheFront() {
        assertEquals(0, PlayNextOrder.insertIndex(-1, 3) { false })
    }

    @Test
    fun neverBeyondTheEnd() {
        assertEquals(2, PlayNextOrder.insertIndex(0, 2) { true })
    }
}
