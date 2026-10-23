package com.localplayer.music.playback

import kotlin.random.Random

/**
 * Pure ordering rules for the Shuffle switch.
 *
 * INVARIANT (queue, Shuffle switch and queue UI): the physical queue order IS the playback order, always.
 * The service installs an unshuffled Media3 order, so nothing is hidden behind the list the user sees.
 *
 *  - Shuffle switch ON means: every queue that is STARTED (replaced: tap a song, Play all, Shuffle all) is
 *    shuffled once with the started song first, and turning the switch on reorders the upcoming songs once.
 *  - Songs added later follow plain queue rules: Play next = right after the current song (newest first),
 *    Add to queue = end.
 *  - Songs queued with Play next stay at the front when Shuffle is turned on.
 *  - Switch OFF puts the not-yet-played songs back in the order they had before Shuffle (by [Slot.rank]),
 *    continuing from the current song and wrapping. Songs added while shuffled follow at the end. If the user
 *    reorders by hand while Shuffle is on, all ranks are dropped: that order is the user's and OFF keeps it.
 */
object ShuffleQueue {
    /** [rank] = position the song had in its list when Shuffle was applied, or -1 when unknown. */
    data class Slot(val playNext: Boolean, val rank: Int)

    /** Length of the leading run of Play next songs; these are never moved. */
    fun pinnedCount(upcoming: List<Slot>): Int {
        var n = 0
        while (n < upcoming.size && upcoming[n].playNext) n++
        return n
    }

    /** New order of [upcoming] for Shuffle on, as indices into [upcoming]. */
    fun shuffledOrder(upcoming: List<Slot>, random: Random): List<Int> {
        val pinned = pinnedCount(upcoming)
        val rest = (pinned until upcoming.size).toMutableList()
        rest.shuffle(random)
        return (0 until pinned).toList() + rest
    }

    /**
     * Order of a freshly started queue of [count] songs when Shuffle is on: [startIndex] first, all others
     * shuffled. Indices into the original list (they double as the ranks).
     */
    fun replacementOrder(count: Int, startIndex: Int, random: Random): List<Int> {
        if (count <= 0) return emptyList()
        val start = startIndex.coerceIn(0, count - 1)
        val others = (0 until count).filter { it != start }.toMutableList()
        others.shuffle(random)
        return listOf(start) + others
    }

    /**
     * New order of [upcoming] for Shuffle off, as indices into [upcoming]. [currentRank] = rank of the current
     * song (-1 if none): songs ranked after it come first, then the ranked ones before it (wrap-around).
     */
    fun restoredOrder(upcoming: List<Slot>, currentRank: Int = -1): List<Int> {
        val pinned = pinnedCount(upcoming)
        val rest = (pinned until upcoming.size).toList()
        val ranked = rest.filter { upcoming[it].rank >= 0 }.sortedBy { upcoming[it].rank }
        val unranked = rest.filter { upcoming[it].rank < 0 }
        val ordered = if (currentRank >= 0) {
            ranked.filter { upcoming[it].rank > currentRank } + ranked.filter { upcoming[it].rank <= currentRank }
        } else {
            ranked
        }
        return (0 until pinned).toList() + ordered + unranked
    }
}
