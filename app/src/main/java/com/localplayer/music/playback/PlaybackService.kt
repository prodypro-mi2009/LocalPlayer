package com.localplayer.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.MainActivity
import com.localplayer.music.R
import com.localplayer.music.AppContainer
import com.localplayer.music.playback.session.SessionRestorePlan
import com.localplayer.music.playback.session.SessionStoreCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the ExoPlayer and the MediaSession. Media3 automatically runs this service in the
 * foreground (with the media notification) only while playback is active, and exposes
 * lock-screen, Bluetooth and headset controls through the session.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.localplayer.music.data.AppLanguage.wrap(base))
    }

    private var mediaSession: MediaSession? = null
    private var historyRecorder: HistoryRecorder? = null
    private var listeningRecorder: ListeningRecorder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var consecutiveErrorSkips = 0
    private var sessionKeeper: PlaybackSessionKeeper? = null
    private var sessionStore: SessionStoreCore? = null
    private var sessionExecutor: ExecutorService? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LocalPlayerApp).container

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The queue order IS the playback order (Shuffle reorders the real queue, see QueueOps / ShuffleQueue), so
        // Media3's own hidden shuffle order is switched off: with it, Play next / Add / reorder / Clear upcoming
        // would act on a different order than the one that plays.
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))

        // MP4 files may contain video; this is a music player, so the video track is never decoded.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(player, error.errorCode) { trackId -> container.database.trackDao().markUnavailable(trackId) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // A track that reaches READY proves playback works again: the skip budget starts over.
                if (playbackState == Player.STATE_READY) consecutiveErrorSkips = 0
            }
        })

        // Saved playback session: all disk work happens on this single background thread.
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "playback-session").apply { isDaemon = true } }
        val store = SessionStoreCore(File(filesDir, "playback-session"), executor, { Log.w(TAG, it) })
        sessionExecutor = executor
        sessionStore = store
        sessionKeeper = PlaybackSessionKeeper(player, store, log = { Log.w(TAG, it) }).also { it.start() }
        scope.launch { restoreSession(player, container, store) }

        historyRecorder = HistoryRecorder(player, container.database.historyDao(), scope)
        listeningRecorder = ListeningRecorder(player, container.stats)
        SleepTimer.attach(player) { stopSelf() }
        com.localplayer.music.widget.WidgetController.setPlayer(this, player)
        AudioEffectsHub.setPlayer(container, player)    // does nothing unless ReplayGain / equalizer are switched on

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Standard Media3 MediaStyle notification, owned by this service (not by the Activity),
        // with a monochrome small icon, ongoing while music is playing.
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(OngoingMediaNotificationProvider(notificationProvider))

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(ArtworkBitmapLoader(container.artwork, scope))
            .build()
    }

    /**
     * Puts the saved queue back after the service was recreated (app swiped away, process killed). The player
     * only gets it when it is still EMPTY and the session was not closed meanwhile, so a song the user picked in
     * the meantime is never replaced. It is prepared but NEVER started: the song waits, ready for Play.
     */
    private suspend fun restoreSession(player: ExoPlayer, container: AppContainer, store: SessionStoreCore) {
        val saved = withContext(Dispatchers.IO) { store.load() } ?: return
        val resolved = withContext(Dispatchers.IO) {
            val uris = saved.queue.entries.map { it.trackUri }.distinct()
            val tracks = uris.chunked(400).flatMap { container.database.trackDao().itemsByUris(it) }.associateBy { it.uri }
            val plan = SessionRestorePlan.plan(saved, tracks.keys) ?: return@withContext null
            val ctx = container.localizedContext()
            val unknownArtist = ctx.getString(R.string.unknown_artist)
            val unknownAlbum = ctx.getString(R.string.unknown_album)
            val items = plan.entries.map { e ->
                MediaItemFactory.restored(tracks.getValue(e.trackUri), unknownArtist, unknownAlbum, e.occurrenceId, e.playNext, e.rank)
            }
            Triple(plan, items, saved.state)
        }
        if (resolved == null) {
            // None of the saved songs exists any more.
            if (player.mediaItemCount == 0 && sessionKeeper?.isFrozen != true) store.invalidate()
            return
        }
        val (plan, items, state) = resolved
        // Never over an active queue, a song the user picked meanwhile (even if the queue is empty again), a
        // closing app, or a service that was already torn down.
        if (player.mediaItemCount != 0 || sessionKeeper?.queueTouched != false || sessionKeeper?.isFrozen == true || sessionStore !== store) return
        player.setMediaItems(items, plan.startIndex, plan.positionMs)
        player.shuffleModeEnabled = state?.shuffle ?: false
        player.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
        player.playWhenReady = false
        player.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * A queue entry failed to play. [PlaybackErrorPolicy] decides from the error code:
     *  - the file is demonstrably at fault: skip it and mark the track unavailable;
     *  - cannot be played now but the file is not proven gone: skip it, do NOT mark the track;
     *  - not the track's fault (audio output, timeouts, unknown): keep the queue and pause (Play retries);
     *  - too many skips in a row without one successful start: stop skipping and pause.
     * Only `removeMediaItem` is used on the failing entry, so the other entries keep their occurrence ids and
     * the saved session follows the queue change; the entry that takes its place continues (Repeat ALL wraps from
     * the last entry; Repeat OFF / ONE pause after the last one).
     */
    private fun handlePlayerError(player: ExoPlayer, errorCode: Int, markUnavailable: suspend (Long) -> Unit) {
        val item = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        when (val decision = PlaybackErrorPolicy.decide(errorCode, consecutiveErrorSkips)) {
            PlaybackErrorPolicy.Decision.PauseKeep -> {
                Log.w(TAG, "playback error $errorCode: queue kept, paused")
                player.pause()
            }
            is PlaybackErrorPolicy.Decision.Skip -> {
                consecutiveErrorSkips++
                if (decision.markUnavailable) {
                    item.mediaId.toLongOrNull()?.let { trackId -> scope.launch { markUnavailable(trackId) } }
                }
                val wasLast = index == player.mediaItemCount - 1
                player.removeMediaItem(index)
                if (PlaybackErrorPolicy.pauseAfterRemoval(wasLast, player.mediaItemCount, player.repeatMode)) {
                    player.pause()
                } else {
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    /**
     * The user swiped the app away from Recent Apps: that means "close the app".
     * Stop playback and the service; resources are released in [onDestroy] and the
     * foreground notification goes away with the service.
     * (Home, screen off, lock screen and switching apps do NOT call this.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Save the live queue and position FIRST: nothing below (super, stop, clear) may touch the saved session.
        sessionKeeper?.freezeAndSave(SAVE_ON_EXIT_TIMEOUT_MS)
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.let {
            it.stop()
            it.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        // Final save for a stop by the system (a no-op after onTaskRemoved or after an intentional clear).
        sessionKeeper?.freezeAndSave(SAVE_ON_EXIT_TIMEOUT_MS)
        sessionKeeper?.release()
        sessionKeeper = null
        sessionExecutor?.shutdown()
        sessionExecutor = null
        sessionStore = null
        SleepTimer.detach()
        com.localplayer.music.widget.WidgetController.setPlayer(this, null)
        AudioEffectsHub.setPlayer(null, null)
        historyRecorder?.release()
        historyRecorder = null
        listeningRecorder?.release()
        listeningRecorder = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PlaybackService"
        /** Upper bound for waiting on the background writer when the app is closed (no disk I/O on this thread). */
        const val SAVE_ON_EXIT_TIMEOUT_MS = 1_500L
    }
}
