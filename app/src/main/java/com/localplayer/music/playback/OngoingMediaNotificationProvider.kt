package com.localplayer.music.playback

import android.app.Notification
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's standard MediaStyle notification provider (artwork, title, artist,
 * previous / play-pause / next, seek bar through the system UI) and, while music is
 * actually playing, marks the notification ongoing so it cannot be swiped away.
 * When playback is paused the notification is dismissible again, like any media notification.
 */
@UnstableApi
class OngoingMediaNotificationProvider(
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val result = delegate.createNotification(
            mediaSession,
            mediaButtonPreferences,
            actionFactory,
            onNotificationChangedCallback,
        )
        val player = mediaSession.player
        val isPlaying = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        if (isPlaying) {
            result.notification.flags = result.notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR
        }
        return result
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    // Abstract since Media3 1.10: the channel is the one the wrapped default provider creates.
    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}
