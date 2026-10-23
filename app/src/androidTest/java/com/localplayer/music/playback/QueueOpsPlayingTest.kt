package com.localplayer.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localplayer.music.playback.TestMedia.onMain
import com.localplayer.music.playback.TestMedia.onMainResult
import com.localplayer.music.playback.TestMedia.waitUntil
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clear on a player that REALLY plays: short silent WAV files are prepared and played, and the test only goes on
 * once `isPlaying` is true. Verifies: same item keeps playing, no media-item transition, playback stays active,
 * no seek or restart. Needs a device or emulator with an audio output; not run in the authoring environment.
 */
@RunWith(AndroidJUnit4::class)
class QueueOpsPlayingTest {
    private lateinit var player: ExoPlayer
    private lateinit var files: List<File>
    private var transitions = 0
    private var discontinuities = 0

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        files = (1..5).map { TestMedia.silentWav(ctx.cacheDir, "silence-$it.wav", seconds = 30) }
        onMain {
            player = ExoPlayer.Builder(ctx).build()
            player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) discontinuities++
                }
            })
        }
    }

    @After fun tearDown() = onMain { player.release() }

    private fun realItem(n: Int): MediaItem =
        MediaItemFactory.create(TestMedia.track(n, Uri.fromFile(files[n - 1]).toString()), "?", "?")

    private fun playFrom(start: Int, playing: Boolean) {
        onMain {
            player.setMediaItems((1..5).map { realItem(it) }, start, 0L)
            player.prepare()
            player.playWhenReady = playing
        }
        assertTrue("player READY", waitUntil { player.playbackState == Player.STATE_READY })
        if (playing) assertTrue("really playing", waitUntil { player.isPlaying })
        // let it run a little so the position is clearly past zero
        assertTrue(waitUntil { !playing || player.currentPosition > 400L })
        onMain { transitions = 0; discontinuities = 0 }
    }

    private fun playingClear(start: Int) {
        playFrom(start, playing = true)
        val (occBefore, posBefore) = onMainResult { MediaItemFactory.occurrenceId(player.currentMediaItem!!) to player.currentPosition }
        val cleared = onMainResult { QueueOps.clearExceptCurrent(player) }
        assertNotNull(cleared)
        Thread.sleep(700)
        onMain {
            assertEquals(1, player.mediaItemCount)
            assertEquals(occBefore, MediaItemFactory.occurrenceId(player.currentMediaItem!!))
            assertTrue("still playing", player.isPlaying)
            assertEquals(Player.STATE_READY, player.playbackState)
            assertTrue("position advanced, no restart", player.currentPosition > posBefore)
            assertEquals("no media item transition", 0, transitions)
            assertEquals("no seek / restart", 0, discontinuities)
        }
    }

    @Test fun playingClearAtStart() = playingClear(0)
    @Test fun playingClearInMiddle() = playingClear(2)
    @Test fun playingClearAtEnd() = playingClear(4)

    @Test fun pausedClearKeepsPositionAndStaysPaused() {
        playFrom(2, playing = false)
        onMain { player.seekTo(3_000L) }
        assertTrue(waitUntil { player.currentPosition >= 2_900L })
        val posBefore = onMainResult { player.currentPosition }
        onMain { assertNotNull(QueueOps.clearExceptCurrent(player)) }
        Thread.sleep(500)
        onMain {
            assertFalse(player.isPlaying)
            assertFalse(player.playWhenReady)
            assertEquals(posBefore, player.currentPosition)
            assertEquals(1, player.mediaItemCount)
        }
    }

    @Test fun clearUpcomingWhilePlayingKeepsTheCurrentSongPlaying() {
        playFrom(1, playing = true)
        onMain { assertNotNull(QueueOps.clearUpcoming(player)) }
        Thread.sleep(500)
        onMain {
            assertTrue(player.isPlaying)
            assertEquals(2, player.mediaItemCount)
            assertEquals(0, transitions)
        }
    }

    @Test fun reorderingWhilePlayingDoesNotInterruptTheCurrentSong() {
        playFrom(0, playing = true)
        onMain { QueueOps.reorderUpcoming(player, true) ; player.shuffleModeEnabled = true }
        Thread.sleep(500)
        onMain {
            assertTrue(player.isPlaying)
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals(0, transitions)
        }
    }

    @Test fun repeatAllClearUpcomingWhilePlayingLeavesOnlyTheCurrentSong() {
        playFrom(2, playing = true)
        onMain {
            player.repeatMode = Player.REPEAT_MODE_ALL
            assertNotNull(QueueOps.clearUpcoming(player))
        }
        Thread.sleep(500)
        onMain {
            assertEquals(1, player.mediaItemCount)
            assertTrue(player.isPlaying)
            assertEquals(0, transitions)
        }
    }
}
