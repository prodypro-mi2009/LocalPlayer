package com.localplayer.music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.MainActivity
import com.localplayer.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        // MP4 files may contain video; this is a music player, so the video track is never decoded.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(player) { trackId -> container.database.trackDao().markUnavailable(trackId) }
            }
        })

        historyRecorder = HistoryRecorder(player, container.database.historyDao(), scope)
        SleepTimer.attach(player) { stopSelf() }

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * A file that cannot be played (deleted, storage removed, corrupt) is flagged unavailable
     * in the library, dropped from the queue, and playback continues with the next track.
     */
    private fun handlePlayerError(player: ExoPlayer, markUnavailable: suspend (Long) -> Unit) {
        val item = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        item.mediaId.toLongOrNull()?.let { trackId ->
            scope.launch { markUnavailable(trackId) }
        }
        val wasLast = index == player.mediaItemCount - 1
        player.removeMediaItem(index)
        if (wasLast || player.mediaItemCount == 0) {
            player.pause()
        } else {
            player.prepare()
            player.play()
        }
    }

    /**
     * The user swiped the app away from Recent Apps: that means "close the app".
     * Stop playback and the service; resources are released in [onDestroy] and the
     * foreground notification goes away with the service.
     * (Home, screen off, lock screen and switching apps do NOT call this.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.let {
            it.stop()
            it.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        SleepTimer.detach()
        historyRecorder?.release()
        historyRecorder = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
