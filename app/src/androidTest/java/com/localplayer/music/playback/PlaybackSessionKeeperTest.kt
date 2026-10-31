package com.localplayer.music.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
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
import java.util.concurrent.atomic.AtomicBoolean
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

    /** Like [scenario], but lets Media3 deliver its queued player events before the test finishes. */
    private fun asyncScenario(block: (ExoPlayer, PlaybackSessionKeeper, SessionStoreCore, (Throwable?) -> Unit) -> Unit) {
        val dir: File = Files.createTempDirectory("keeper-async").toFile()
        val exec = Executors.newSingleThreadExecutor()
        val store = SessionStoreCore(dir, exec)
        val main = Handler(Looper.getMainLooper())
        var failure: Throwable? = null
        var cleanup: (() -> Unit)? = null
        val finished = AtomicBoolean(false)
        val done = CountDownLatch(1)
        main.post {
            val player = ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build()
            player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
            val keeper = PlaybackSessionKeeper(player, store).also { it.start() }
            cleanup = { keeper.release(); player.release() }
            val finish: (Throwable?) -> Unit = { error ->
                if (finished.compareAndSet(false, true)) {
                    failure = error
                    main.post {
                        cleanup?.invoke()
                        done.countDown()
                    }
                }
            }
            try { block(player, keeper, store, finish) } catch (t: Throwable) { finish(t) }
        }
        val completed = done.await(30, TimeUnit.SECONDS)
        if (!completed) main.post { cleanup?.invoke() }
        exec.shutdown()
        assertTrue("player event callback did not complete", completed)
        failure?.let { throw it }
    }

    private fun items(vararg n: Int) = n.map { MediaItemFactory.create(track(it), "?", "?") }

    @Test fun queueAndPositionAreSaved() = asyncScenario { player, _, store, finish ->
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!events.contains(Player.EVENT_TIMELINE_CHANGED)) return
                player.removeListener(this)
                try {
                    assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
                    val saved = store.load()
                    assertNotNull(saved)
                    assertEquals(3, saved!!.queue.entries.size)
                    assertEquals(MediaItemFactory.occurrenceId(player.getMediaItemAt(1)), saved.state!!.currentOccurrenceId)
                    assertEquals(12_345L, saved.state!!.positionMs)
                    assertTrue(saved.state!!.shuffle)
                    finish(null)
                } catch (t: Throwable) {
                    finish(t)
                }
            }
        })
        player.setMediaItems(items(1, 2, 3), 1, 12_345L)
        player.shuffleModeEnabled = true
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

    @Test fun timelineUpdatesWithoutQueueEditsDoNotRewriteTheQueue() = asyncScenario { player, _, store, finish ->
        var firstGeneration: Long? = null
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                try {
                    if (firstGeneration == null && events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
                        firstGeneration = store.load()?.queue?.generation
                        assertNotNull(firstGeneration)
                        player.seekTo(1, 1_000L) // a position/timeline update, not a queue edit
                    } else if (firstGeneration != null && events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                        assertEquals(SessionStoreCore.SaveOutcome.DURABLE, store.flushBlocking(5_000))
                        assertEquals(firstGeneration, store.load()?.queue?.generation)
                        player.removeListener(this)
                        finish(null)
                    }
                } catch (t: Throwable) {
                    player.removeListener(this)
                    finish(t)
                }
            }
        })
        player.setMediaItems(items(1, 2), 0, 0L)
    }
}
