package com.localplayer.music.playback

import com.localplayer.music.playback.ShuffleQueue.Slot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleQueueTest {
    private fun slots(vararg playNext: Boolean) = playNext.map { Slot(it, -1) }

    @Test fun pinnedIsOnlyTheLeadingPlayNextRun() {
        assertEquals(2, ShuffleQueue.pinnedCount(slots(true, true, false, true)))
        assertEquals(0, ShuffleQueue.pinnedCount(slots(false, true)))
        assertEquals(0, ShuffleQueue.pinnedCount(emptyList()))
    }

    @Test fun shuffleKeepsPinnedFirstAndUsesEveryIndexOnce() {
        val upcoming = slots(true, true, false, false, false, false, false)
        repeat(50) { seed ->
            val order = ShuffleQueue.shuffledOrder(upcoming, Random(seed))
            assertEquals(listOf(0, 1), order.take(2))
            assertEquals((0 until 7).toList(), order.sorted())
        }
    }

    @Test fun shuffleActuallyReordersLargerLists() {
        val upcoming = slots(*BooleanArray(30) { false })
        val order = ShuffleQueue.shuffledOrder(upcoming, Random(1))
        assertTrue(order != (0 until 30).toList())
    }

    @Test fun restoreSortsByRankAndKeepsUnrankedAtTheEnd() {
        // queue when Shuffle was on: [playNext], then ranks 5,2,9, then two songs added while shuffled
        val upcoming = listOf(Slot(true, -1), Slot(false, 5), Slot(false, 2), Slot(false, 9), Slot(false, -1), Slot(false, -1))
        assertEquals(listOf(0, 2, 1, 3, 4, 5), ShuffleQueue.restoredOrder(upcoming))
    }

    @Test fun shuffleOnThenOffRoundTripsTheOriginalOrder() {
        // Shuffle on: rank = queue position before shuffling
        val before = (0 until 12).toList()
        val shuffled = ShuffleQueue.shuffledOrder(slots(*BooleanArray(12) { false }), Random(7))
        val afterOn = shuffled.map { Slot(false, before[it]) }
        val back = ShuffleQueue.restoredOrder(afterOn).map { afterOn[it].rank }
        assertEquals(before, back)
    }

    @Test fun smallListsAreSafe() {
        assertEquals(emptyList<Int>(), ShuffleQueue.shuffledOrder(emptyList(), Random(0)))
        assertEquals(listOf(0), ShuffleQueue.shuffledOrder(slots(false), Random(0)))
        assertEquals(listOf(0), ShuffleQueue.restoredOrder(slots(false)))
    }

    @Test fun replacementKeepsTheStartedSongFirstAndUsesEverySongOnce() {
        repeat(40) { seed ->
            val order = ShuffleQueue.replacementOrder(10, 6, Random(seed))
            assertEquals(6, order.first())
            assertEquals((0 until 10).toList(), order.sorted())
        }
        assertEquals(emptyList<Int>(), ShuffleQueue.replacementOrder(0, 0, Random(1)))
        assertEquals(listOf(0), ShuffleQueue.replacementOrder(1, 0, Random(1)))
    }

    @Test fun repeatedQueueReplacementsNeverCarryRanksOrFlagsOver() {
        // Each replacement builds its slots from clean items: no Play next flag, ranks = indices of THAT list.
        val first = ShuffleQueue.replacementOrder(6, 2, Random(5)).map { Slot(false, it) }
        val second = ShuffleQueue.replacementOrder(4, 0, Random(9)).map { Slot(false, it) }
        assertEquals(setOf(0, 1, 2, 3), second.map { it.rank }.toSet())
        assertTrue(second.none { it.playNext })
        assertEquals(6, first.size)
    }

    @Test fun shuffleAllThenOffContinuesFromTheCurrentSongInListOrder() {
        // list 0..9, Shuffle all started at song 6 -> queue [6, others shuffled], ranks = list index
        val order = ShuffleQueue.replacementOrder(10, 6, Random(2))
        val slots = order.map { Slot(false, it) }
        val currentRank = slots.first().rank // 6
        val upcoming = slots.drop(1)
        val back = ShuffleQueue.restoredOrder(upcoming, currentRank).map { upcoming[it].rank }
        assertEquals(listOf(7, 8, 9, 0, 1, 2, 3, 4, 5), back)
    }

    @Test fun afterPlayingSomeSongsOffContinuesFromWhereThePlayerIs() {
        // shuffled queue of ranks; the player is on rank 4; unplayed: 9, 1, 7
        val upcoming = listOf(9, 1, 7).map { Slot(false, it) }
        assertEquals(listOf(7, 9, 1), ShuffleQueue.restoredOrder(upcoming, 4).map { upcoming[it].rank })
    }

    @Test fun manualReorderPolicyDroppedRanksMeanOffKeepsTheUsersOrder() {
        // after a manual drag all ranks are -1: Shuffle off must not move anything
        val upcoming = (0 until 6).map { Slot(false, -1) }
        assertEquals((0 until 6).toList(), ShuffleQueue.restoredOrder(upcoming, 3))
    }

    @Test fun playNextSongsKeepTheirNewestFirstOrderWhenShuffleTurnsOnAndOff() {
        // Z was queued after E, so the queue is [Z, E, ...rest]; both are pinned in that order.
        val upcoming = listOf(Slot(true, -1), Slot(true, -1)) + (0 until 6).map { Slot(false, it + 2) }
        val on = ShuffleQueue.shuffledOrder(upcoming, Random(3))
        assertEquals(listOf(0, 1), on.take(2))
        val off = ShuffleQueue.restoredOrder(upcoming)
        assertEquals(listOf(0, 1), off.take(2))
    }
}
