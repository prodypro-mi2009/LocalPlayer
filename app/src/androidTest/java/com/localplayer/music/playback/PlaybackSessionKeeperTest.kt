package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localplayer.music.data.db.TrackListItem
import com.localplayer.music.playback.session.SessionStoreCore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The service-side keeper against a real ExoPlayer: what is saved when the queue changes, that an intentional
 * clear deletes the session, and that closing the app (freezeAndSave) is not undone by the clear that follows it.
 * Needs a device or emulator; not run in the authoring environment.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionKeeperTest {
    private fun track(n: Int) = TrackListItem(
        id = n.toLong(), uri = "content://test/$n", title = "T$n", artist = null, primaryArtist = null,
        primaryArtistKey = null, album = null, albumArtist = null, albumGroupKey = null, genre = null, year = null,
        trackNumber = null, discNumber = null, durationMs = 180_000L, hasArtwork = false, lastModified = 1L,
    )

    private fun scenario(block: (ExoPlayer, PlaybackSessionKeeper, SessionStoreCore) -> Unit) {
        val dir: File = Files.createTempDirectory("keeper").toFile()
        val exec = Executors.newSingleThreadExecutor()
        val store = SessionStoreCore(dir, exec)
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            val player = ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build()
            player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
            val keeper = PlaybackSessionKeeper(player, store).also { it.start() }
            try { block(player, keeper, store) } catch (t: Throwable) { failure = t } finally {
                keeper.release(); player.release(); done.countDown()
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        exec.shutdown()
        failure?.let { throw it }
    }

    private fun items(vararg n: Int) = n.map { MediaItemFactory.create(track(it), "?", "?") }

    @Test fun queueAndPositionAreSaved() = scenario { player, _, store ->
        player.setMediaItems(items(1, 2, 3), 1, 12_345L)
        player.shuffleModeEnabled = true
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
        val saved = store.load()
        assertNotNull(saved)
        assertEquals(3, saved!!.queue.entries.size)
        assertEquals(MediaItemFactory.occurrenceId(player.getMediaItemAt(1)), saved.state!!.currentOccurrenceId)
        assertTrue(saved.state!!.shuffle)
    }

    @Test fun intentionalClearDeletesTheSession() = scenario { player, _, store ->
        player.setMediaItems(items(1, 2), 0, 0L)
        store.flushBlocking(5_000)
        player.clearMediaItems()
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
        assertNull(store.load())
    }

    @Test fun closingTheAppSavesBeforeTheLiveQueueIsCleared() = scenario { player, keeper, store ->
        player.setMediaItems(items(1, 2, 3), 2, 7_000L)
        val currentOccurrence = MediaItemFactory.occurrenceId(player.currentMediaItem!!)
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, keeper.freezeAndSave(5_000))
        // what onTaskRemoved does next:
        player.stop()
        player.clearMediaItems()
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
        val saved = store.load()
        assertNotNull("the saved session survives the cleanup", saved)
        assertEquals(3, saved!!.queue.entries.size)
        assertEquals(currentOccurrence, saved.state!!.currentOccurrenceId)
    }

    @Test fun queueTouchedBlocksALateRestoreEvenWhenTheQueueIsEmptyAgain() = scenario { player, keeper, _ ->
        assertEquals(false, keeper.queueTouched)
        player.setMediaItems(items(1), 0, 0L) // the user picked a song...
        player.clearMediaItems() // ...and it is gone again
        assertEquals(true, keeper.queueTouched)
    }

    @Test fun timelineUpdatesWithoutQueueEditsDoNotRewriteTheQueue() = scenario { player, _, store ->
        player.setMediaItems(items(1, 2), 0, 0L)
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
        val first = store.load()!!.queue.generation
        player.seekTo(1, 1_000L) // not a queue edit
        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
        assertEquals(first, store.load()!!.queue.generation)
    }
}
