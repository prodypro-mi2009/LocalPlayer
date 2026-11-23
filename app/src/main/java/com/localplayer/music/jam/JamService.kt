package com.localplayer.music.jam

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.MainActivity
import com.localplayer.music.R

/**
 * Keeps this phone's Jam alive (with a notification) for as long as the session lasts. The notification shows the song
 * that is playing in the Jam and has the "ask for previous / next" buttons (everybody votes).
 */
class JamService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15+ limits how long a "data sync" service may run. When the time is up the service must stop at once
     * (otherwise the system crashes the app): the Jam is ended cleanly and the person is told why.
     */
    @SuppressLint("MissingPermission")
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as LocalPlayerApp).container.jam.leave()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java).notify(
                TIMEOUT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.jam_notif_channel))
                    .setContentText(getString(R.string.jam_timeout))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "jam"
        private const val NOTIFICATION_ID = 4108
        private const val TIMEOUT_NOTIFICATION_ID = 4109

        @Volatile
        private var song: Pair<String, String>? = null

        @Volatile
        private var running = false

        private fun build(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.jam_notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val playing = song
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(playing?.first ?: context.getString(R.string.jam_notif_title))
                .setContentText(playing?.second?.takeIf { it.isNotBlank() } ?: context.getString(R.string.jam_notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
            if (playing != null) {
                builder.addAction(0, context.getString(R.string.jam_ask_prev), ask(context, JamAction.PREVIOUS))
                builder.addAction(0, context.getString(R.string.jam_ask_next), ask(context, JamAction.NEXT))
            }
            return builder.build()
        }

        private fun ask(context: Context, action: JamAction): PendingIntent {
            val intent = Intent(context, JamVoteReceiver::class.java)
                .setAction(JamVoteNotifier.ACTION_ASK)
                .putExtra(JamVoteNotifier.EXTRA_ACTION, action.wire)
            return PendingIntent.getBroadcast(
                context,
                if (action == JamAction.NEXT) 3 else 4,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun start(context: Context) {
            try {
                running = true
                ContextCompat.startForegroundService(context, Intent(context, JamService::class.java))
            } catch (e: IllegalStateException) {
                // The app is in the background and Android does not allow it now; the Jam still works while the app is open.
            }
        }

        /** The song now playing in the Jam (null = none): shown in the notification. */
        @SuppressLint("MissingPermission")
        fun showSong(context: Context, title: String?, artist: String?) {
            song = if (title.isNullOrBlank()) null else title to artist.orEmpty()
            if (!running) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context))
        }

        fun stop(context: Context) {
            running = false
            song = null
            context.stopService(Intent(context, JamService::class.java))
        }
    }
}
