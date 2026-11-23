package com.localplayer.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
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
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
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
    private var trackFader: TrackFader? = null
    private var favoriteJob: Job? = null
    private var appContainer: AppContainer? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LocalPlayerApp).container
        appContainer = container

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

        // Saved playback session: all disk work happens on this single background thread. The user can switch the
        // restore off (Settings > Audio > Playback): then nothing is saved and an old saved queue is deleted.
        if (container.settings.restoreQueue.value) {
            val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "playback-session").apply { isDaemon = true } }
            val store = SessionStoreCore(File(filesDir, "playback-session"), executor, { Log.w(TAG, it) })
            sessionExecutor = executor
            sessionStore = store
            sessionKeeper = PlaybackSessionKeeper(player, store, log = { Log.w(TAG, it) }).also { it.start() }
            scope.launch { restoreSession(player, container, store) }
        } else {
            scope.launch(Dispatchers.IO) { runCatching { File(filesDir, "playback-session").deleteRecursively() } }
        }

        // Opt-in fade between songs: nothing is created or scheduled until the switch is on.
        val fader = TrackFader(player) { container.settings.fadeSeconds.value * 1000L }
        trackFader = fader
        scope.launch { container.settings.fadeBetweenSongs.collect { on -> if (on) fader.start() else fader.stop() } }

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
            .setCallback(ControllerGate())
            .setBitmapLoader(ArtworkBitmapLoader(container.artwork, scope))
            .build()

        // The heart button of the notification / lock screen: follows the favourite state of the current song.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                observeFavorite(player, container)
            }
        })
        observeFavorite(player, container)
    }

    /** Watches whether the current song is a favourite (one small Room query) and shows the matching heart button. */
    private fun observeFavorite(player: Player, container: AppContainer) {
        favoriteJob?.cancel()
        val uri = player.currentMediaItem?.let { MediaItemFactory.libraryUri(it) }
        if (uri == null) {
            mediaSession?.setMediaButtonPreferences(ImmutableList.of())
            return
        }
        favoriteJob = scope.launch {
            container.library.isFavorite(uri).collect { favorite -> showHeart(favorite) }
        }
    }

    private fun showHeart(favorite: Boolean) {
        val button = CommandButton.Builder(if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(getString(if (favorite) R.string.remove_favorite else R.string.add_favorite))
            .setSessionCommand(TOGGLE_FAVORITE)
            .build()
        mediaSession?.setMediaButtonPreferences(ImmutableList.of(button))
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
     * The service is exported (the system, Bluetooth, the lock screen and Android Auto must reach it), and Media3's
     * default accepts every app that asks. This keeps full control for this app, trusted callers (the system, apps with
     * the media-control permission) and a few known media hosts; any other app may connect but only READ what is playing.
     * A limited connection is logged, so a legitimate remote that stopped working can be recognised by its package name.
     */
    private inner class ControllerGate : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (controller.packageName == packageName || controller.isTrusted || controller.packageName in KNOWN_HOSTS) {
                return builder
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(TOGGLE_FAVORITE).build(),
                    )
                    .build()
            }
            Log.w(TAG, "controller ${controller.packageName} limited to read-only")
            return builder.setAvailablePlayerCommands(READ_ONLY_COMMANDS).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == TOGGLE_FAVORITE.customAction) {
                val uri = session.player.currentMediaItem?.let { MediaItemFactory.libraryUri(it) }
                val library = appContainer?.library
                if (uri != null && library != null) {
                    // The button follows the database, so it changes by itself when this finishes.
                    scope.launch(Dispatchers.IO) { library.toggleFavorite(uri) }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

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
     * The user swiped the app away from Recent Apps. By default that means "close the app": stop playback and the
     * service; resources are released in [onDestroy] and the foreground notification goes away with the service.
     * With "keep the player after closing" (Settings > Playback) the service stays instead (see below).
     * (Home, screen off, lock screen and switching apps do NOT call this.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepPlayer = appContainer?.settings?.keepPlayerAfterClose?.value == true
        val live = mediaSession?.player
        if (keepPlayer && live != null && live.mediaItemCount > 0) {
            // The service and its notification stay (Media3 itself ends the foreground state after ten idle minutes).
            // A song that plays simply keeps playing. A paused one is STOPPED, not cleared: the queue and the position
            // stay, the notification turns into the dimmed player card, and Play prepares the song again. The saved
            // session is NOT frozen here: it keeps following the queue and the position like before.
            val playing = live.playWhenReady &&
                live.playbackState != Player.STATE_IDLE && live.playbackState != Player.STATE_ENDED
            if (!playing) live.stop()
            return
        }
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
        favoriteJob?.cancel()
        favoriteJob = null
        // Final save for a stop by the system (a no-op after onTaskRemoved or after an intentional clear).
        sessionKeeper?.freezeAndSave(SAVE_ON_EXIT_TIMEOUT_MS)
        sessionKeeper?.release()
        sessionKeeper = null
        sessionExecutor?.shutdown()
        sessionExecutor = null
        sessionStore = null
        trackFader?.stop()
        trackFader = null
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

        /** The heart button of the media notification. */
        val TOGGLE_FAVORITE = SessionCommand("com.localplayer.music.TOGGLE_FAVORITE", Bundle.EMPTY)
        /** Upper bound for waiting on the background writer when the app is closed (no disk I/O on this thread). */
        const val SAVE_ON_EXIT_TIMEOUT_MS = 1_500L

        /** Hosts that are not "trusted" by Android's own test but are legitimate controllers of a media app. */
        val KNOWN_HOSTS = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.googlequicksearchbox", // Google Assistant
            "com.google.android.wearable.app", // Wear OS companion
        )

        /** What any other app may do: read the current song and state, nothing that changes playback. */
        val READ_ONLY_COMMANDS: Player.Commands = Player.Commands.Builder()
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
            .build()
    }
}
