package com.localplayer.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localplayer.music.playback.TestMedia.item
import com.localplayer.music.playback.TestMedia.onMain
import com.localplayer.music.playback.TestMedia.onMainResult
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Queue behaviour on a REAL Media3 ExoPlayer (needs a device or emulator). These tests use placeholder media
 * that is never prepared, so they prove playlist state only: order, current item, position, playWhenReady.
 * They do NOT prove that audio keeps playing; that is [QueueOpsPlayingTest], which uses real audio.
 * Not run in the authoring environment (no Android SDK there).
 */
@RunWith(AndroidJUnit4::class)
class QueueOpsMediaTest {
    private lateinit var player: ExoPlayer

    @Before fun setUp() = onMain {
        player = ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build()
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0)) // same as PlaybackService
    }

    @After fun tearDown() = onMain { player.release() }

    private fun load(vararg tracks: Int, start: Int = 0, positionMs: Long = 0L) {
        player.setMediaItems(tracks.map { item(it) }, start, positionMs)
    }

    private fun order(): List<Int> = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId.toInt() }
    private fun currentId(): Int? = player.currentMediaItem?.mediaId?.toInt()
    private fun occ(i: Int) = MediaItemFactory.occurrenceId(player.getMediaItemAt(i))!!

    private fun withShuffle(on: Boolean, seed: Int = 3) {
        QueueOps.reorderUpcoming(player, on, Random(seed))
        player.shuffleModeEnabled = on
    }

    // --- Clear keeps the current song (paused: position is exact because no clock advances) -------------

    private fun pausedClear(start: Int) = onMain {
        load(1, 2, 3, 4, 5, start = start, positionMs = 42_000L)
        player.playWhenReady = false
        val currentBefore = player.currentMediaItem!!
        val positionBefore = player.currentPosition
        assertNotNull(QueueOps.clearExceptCurrent(player))
        assertEquals(listOf(start + 1), order())
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(MediaItemFactory.occurrenceId(currentBefore), MediaItemFactory.occurrenceId(player.currentMediaItem!!))
        assertEquals("paused position is untouched", positionBefore, player.currentPosition)
        assertFalse(player.playWhenReady)
    }

    @Test fun pausedClearAtStart() = pausedClear(0)
    @Test fun pausedClearInMiddle() = pausedClear(2)
    @Test fun pausedClearAtEnd() = pausedClear(4)

    @Test fun clearOnSingleSongQueueDoesNothing() = onMain {
        load(1)
        assertNull(QueueOps.clearExceptCurrent(player))
        assertEquals(listOf(1), order())
    }

    // --- Clear upcoming and Repeat ------------------------------------------------------------------------

    @Test fun clearUpcomingKeepsHistoryWithRepeatOff() = onMain {
        load(1, 2, 3, 4, 5, start = 2)
        val cleared = QueueOps.clearUpcoming(player)
        assertNotNull(cleared)
        assertEquals(listOf(1, 2, 3), order())
        assertEquals(3, currentId())
    }

    @Test fun clearUpcomingKeepsHistoryWithRepeatOne() = onMain {
        load(1, 2, 3, 4, start = 1)
        player.repeatMode = Player.REPEAT_MODE_ONE
        QueueOps.clearUpcoming(player)
        assertEquals(listOf(1, 2), order())
    }

    @Test fun clearUpcomingWithRepeatAllAlsoRemovesTheSongsItWouldWrapTo() = onMain {
        // Documented behaviour: with Repeat ALL the songs before the current one ARE upcoming (the queue wraps),
        // so nothing may be left that would still play after the current song.
        load(1, 2, 3, 4, 5, start = 2)
        player.repeatMode = Player.REPEAT_MODE_ALL
        QueueOps.clearUpcoming(player)
        assertEquals(listOf(3), order())
        assertEquals(3, currentId())
        assertFalse("nothing else to wrap to", player.hasNextMediaItem() && player.nextMediaItemIndex != 0)
    }

    @Test fun clearUpcomingWithRepeatAllUndoRestoresBothSides() = onMain {
        load(1, 2, 3, 4, 5, start = 2)
        player.repeatMode = Player.REPEAT_MODE_ALL
        val cleared = QueueOps.clearUpcoming(player)!!
        assertTrue(QueueOps.undoClear(player, cleared))
        assertEquals(listOf(1, 2, 3, 4, 5), order())
        assertEquals(3, currentId())
    }

    // --- Undo guards ------------------------------------------------------------------------------------

    @Test fun undoClearRestoresWhenNothingChanged() = onMain {
        load(1, 2, 3, 4, 5, start = 2, positionMs = 10_000L)
        val cleared = QueueOps.clearExceptCurrent(player)!!
        assertTrue(QueueOps.undoClear(player, cleared))
        assertEquals(listOf(1, 2, 3, 4, 5), order())
        assertEquals(3, currentId())
    }

    @Test fun undoClearIsRefusedWhenASongWasAddedMeanwhile() = onMain {
        load(1, 2, 3, start = 1)
        val cleared = QueueOps.clearExceptCurrent(player)!!
        QueueOps.appendOccurrence(player, item(9)) // added while the Undo snackbar is visible
        assertFalse(QueueOps.undoClear(player, cleared))
        assertEquals(listOf(2, 9), order())
    }

    @Test fun undoClearIsRefusedWhenTheQueueWasReplaced() = onMain {
        load(1, 2, 3, start = 1)
        val cleared = QueueOps.clearExceptCurrent(player)!!
        player.setMediaItems(listOf(item(2), item(7))) // same current track id but a different queue
        assertFalse(QueueOps.undoClear(player, cleared))
    }

    @Test fun undoClearIsRefusedAfterAReorder() = onMain {
        load(1, 2, 3, 4, start = 0)
        val cleared = QueueOps.clearUpcoming(player)!!
        QueueOps.insertNextOccurrence(player, item(8))
        QueueOps.insertNextOccurrence(player, item(9))
        QueueOps.moveEntry(player, occ(2), 1)
        assertFalse(QueueOps.undoClear(player, cleared))
    }

    // --- Undo of an add ---------------------------------------------------------------------------------

    @Test fun undoOfFirstItemRemovesItWhileNeverStarted() = onMain {
        val added = QueueOps.appendOccurrence(player, item(1))
        assertTrue(added.becameCurrent)
        assertTrue(QueueOps.removeOccurrence(player, added.occurrenceId, neverStarted = true))
        assertEquals(0, player.mediaItemCount)
    }

    @Test fun undoOfFirstItemIsRefusedOnceStartedEvenAtPositionZeroAndPaused() = onMain {
        val added = QueueOps.appendOccurrence(player, item(1))
        // play, then pause again at once: position 0, playWhenReady false, yet it has been started
        assertFalse(QueueOps.removeOccurrence(player, added.occurrenceId, neverStarted = false))
        assertEquals(listOf(1), order())
    }

    @Test fun undoOfFirstItemIsRefusedWhilePlayWhenReadyIsSet() = onMain {
        val added = QueueOps.appendOccurrence(player, item(1))
        player.playWhenReady = true
        assertFalse(QueueOps.removeOccurrence(player, added.occurrenceId, neverStarted = true))
    }

    @Test fun undoRemovesOnlyTheAddedOccurrenceOfADuplicate() = onMain {
        load(1, 2, start = 0)
        val added = QueueOps.appendOccurrence(player, item(2)) // same file as entry 2, intentionally
        assertEquals(listOf(1, 2, 2), order())
        assertTrue(QueueOps.removeOccurrence(player, added.occurrenceId, neverStarted = false))
        assertEquals(listOf(1, 2), order())
        assertEquals(1, currentId())
    }

    @Test fun removingOtherEntriesKeepsCurrentItemAndPosition() = onMain {
        load(1, 2, 3, 4, start = 2, positionMs = 30_000L)
        val before = player.currentPosition
        player.removeMediaItem(0)
        player.removeMediaItem(2)
        assertEquals(listOf(2, 3), order())
        assertEquals(3, currentId())
        assertEquals(before, player.currentPosition)
    }

    // --- Play next: newest first, with Shuffle on and off -----------------------------------------------

    @Test fun newestPlayNextPlaysFirstShuffleOff() = onMain {
        load(1, 2, 3, start = 0)
        QueueOps.insertNextOccurrence(player, item(8)) // E
        QueueOps.insertNextOccurrence(player, item(9)) // Z
        assertEquals(listOf(1, 9, 8, 2, 3), order())
    }

    @Test fun newestPlayNextPlaysFirstWithShuffleOn() = onMain {
        load(1, 2, 3, 4, 5, 6, start = 0)
        withShuffle(true)
        QueueOps.insertNextOccurrence(player, item(8)) // E
        QueueOps.insertNextOccurrence(player, item(9)) // Z
        assertEquals(listOf(9, 8), order().subList(1, 3))
        player.seekToNextMediaItem()
        assertEquals(9, currentId())
        player.seekToNextMediaItem()
        assertEquals(8, currentId())
    }

    @Test fun addToQueueIsAtTheEndWithShuffleOn() = onMain {
        load(1, 2, 3, 4, start = 0)
        withShuffle(true)
        QueueOps.appendOccurrence(player, item(9))
        assertEquals(9, order().last())
    }

    @Test fun clearUpcomingRemovesEveryUpcomingSongWithShuffleOn() = onMain {
        load(1, 2, 3, 4, 5, 6, start = 2)
        withShuffle(true)
        val currentBefore = currentId()
        val past = order().subList(0, player.currentMediaItemIndex)
        QueueOps.clearUpcoming(player)
        assertEquals(past + currentBefore!!, order())
    }

    // --- Shuffle policy -----------------------------------------------------------------------------------

    @Test fun shuffleKeepsCurrentAndPastAndOnlyReordersUpcoming() = onMain {
        load(1, 2, 3, 4, 5, 6, 7, 8, start = 2, positionMs = 5_000L)
        QueueOps.reorderUpcoming(player, true, Random(11))
        assertEquals(listOf(1, 2, 3), order().subList(0, 3))
        assertEquals(3, currentId())
        assertEquals(listOf(4, 5, 6, 7, 8), order().subList(3, 8).sorted())
    }

    @Test fun shuffleOffRestoresTheUpcomingOrder() = onMain {
        load(1, 2, 3, 4, 5, 6, 7, 8, start = 0)
        withShuffle(true)
        withShuffle(false)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), order())
    }

    @Test fun playNextSongsStayFirstWhenShuffleTurnsOn() = onMain {
        load(1, 2, 3, 4, 5, 6, start = 0)
        QueueOps.insertNextOccurrence(player, item(8))
        QueueOps.insertNextOccurrence(player, item(9)) // queue: 1 9 8 ...
        withShuffle(true)
        assertEquals(listOf(9, 8), order().subList(1, 3))
    }

    @Test fun dragReorderWithShuffleOnChangesThePlaybackOrderAndSurvivesShuffleOff() = onMain {
        load(1, 2, 3, 4, 5, start = 0)
        withShuffle(true)
        val movedTrack = order().last()
        QueueOps.moveEntry(player, occ(player.mediaItemCount - 1), 1)
        assertEquals(movedTrack, order()[1])
        val afterDrag = order()
        withShuffle(false) // must not undo the user's deliberate order
        assertEquals(afterDrag, order())
        player.seekToNextMediaItem()
        assertEquals(movedTrack, currentId())
    }

    // --- Replacing a queue ------------------------------------------------------------------------------

    private fun listOfItems(n: Int) = (1..n).map { item(it) }

    @Test fun replacingAQueueWithShuffleOnKeepsTheTappedSongCurrentAndShufflesTheRest() = onMain {
        player.shuffleModeEnabled = true
        QueueOps.replaceQueue(player, listOfItems(12), startIndex = 7, forceShuffle = false, random = Random(4))
        assertEquals(8, order().first())
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals((1..12).toList(), order().sorted())
        assertTrue("rest is shuffled", order().drop(1) != (1..12).filter { it != 8 })
        assertTrue(player.shuffleModeEnabled)
    }

    @Test fun replacingAQueueWithShuffleOffKeepsListOrderAndStartIndex() = onMain {
        QueueOps.replaceQueue(player, listOfItems(6), startIndex = 3, forceShuffle = false)
        assertEquals((1..6).toList(), order())
        assertEquals(4, currentId())
        assertFalse(player.shuffleModeEnabled)
    }

    @Test fun shuffleAllTurnsTheSwitchOnAndOffRestoresTheListOrder() = onMain {
        QueueOps.replaceQueue(player, listOfItems(10), startIndex = 6, forceShuffle = true, random = Random(2))
        assertTrue(player.shuffleModeEnabled)
        assertEquals(7, currentId())
        QueueOps.reorderUpcoming(player, false)
        player.shuffleModeEnabled = false
        assertEquals(listOf(7, 8, 9, 10, 1, 2, 3, 4, 5, 6), order()) // continues from the current song, wraps
    }

    @Test fun normalPlayAllAfterShuffleAllFollowsTheStillEnabledSwitch() = onMain {
        QueueOps.replaceQueue(player, listOfItems(8), 0, forceShuffle = true, random = Random(5))
        QueueOps.replaceQueue(player, listOfItems(8), 0, forceShuffle = false, random = Random(6))
        assertTrue("switch stays on", player.shuffleModeEnabled)
        assertEquals(1, currentId())
        assertTrue("and the queue really is shuffled", order() != (1..8).toList())
    }

    @Test fun repeatedReplacementsLeaveNoStaleRanksOrPlayNextFlags() = onMain {
        QueueOps.replaceQueue(player, listOfItems(6), 0, forceShuffle = true, random = Random(1))
        QueueOps.insertNextOccurrence(player, item(9))
        // replace with items that still carry a flag and a rank from the old queue
        val dirty = MediaItemFactory.withRank(MediaItemFactory.markPlayNext(item(1)), 4)
        QueueOps.replaceQueue(player, listOf(dirty, item(2), item(3)), 0, forceShuffle = false)
        player.shuffleModeEnabled = false
        for (i in 0 until player.mediaItemCount) {
            val it = player.getMediaItemAt(i)
            assertFalse(MediaItemFactory.isPlayNext(it))
            assertEquals(-1, MediaItemFactory.rank(it))
        }
    }

    @Test fun duplicatesOfTheSameFileKeepSeparateOccurrences() = onMain {
        load(1, start = 0)
        QueueOps.appendOccurrence(player, item(1))
        QueueOps.appendOccurrence(player, item(1))
        val ids = (0 until 3).map { occ(it) }
        assertEquals(3, ids.toSet().size)
    }

    @Test fun replacementKeepsOccurrenceIdsOfTheItemsItReceived() = onMain {
        val items = listOfItems(4)
        val ids = items.map { MediaItemFactory.occurrenceId(it)!! }.toSet()
        QueueOps.replaceQueue(player, items, 1, forceShuffle = true, random = Random(8))
        assertEquals(ids, (0 until 4).map { occ(it) }.toSet())
    }

    // value check used by PlaybackErrorPolicy
    @Test fun errorPolicyCodesMatchMedia3() {
        assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackErrorPolicy.IO_FILE_NOT_FOUND)
        assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackErrorPolicy.PARSING_CONTAINER_MALFORMED)
        assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackErrorPolicy.PARSING_CONTAINER_UNSUPPORTED)
        assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackErrorPolicy.DECODING_FORMAT_UNSUPPORTED)
        assertEquals(Player.REPEAT_MODE_ALL, 2)
        val skipOnly = listOf(2000, 2006, 2008, 3002, 3004, 4001, 4002, 4003, 4004)
        val known = listOf(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
        assertEquals(skipOnly, known)
    }
}
