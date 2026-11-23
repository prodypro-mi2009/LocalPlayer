package com.localplayer.music.jam

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.MainActivity
import com.localplayer.music.R

/** The pop-up notification "X asks for the next song" with Yes / No buttons. */
object JamVoteNotifier {
    private const val CHANNEL_ID = "jam_vote"
    private const val NOTIFICATION_ID = 4109
    const val ACTION_VOTE = "com.localplayer.music.jam.VOTE"
    const val ACTION_ASK = "com.localplayer.music.jam.ASK"
    const val EXTRA_ACTION = "action"
    const val EXTRA_ID = "id"
    const val EXTRA_YES = "yes"

    @SuppressLint("MissingPermission")
    fun show(context: Context, proposal: JamProposal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.jam_vote_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val title = context.getString(
            if (proposal.action == JamAction.NEXT) R.string.jam_vote_title_next else R.string.jam_vote_title_prev,
            proposal.byName,
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.jam_vote_text, proposal.needed))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setTimeoutAfter(JAM_VOTE_MS)
            .addAction(0, context.getString(R.string.jam_vote_yes), answer(context, proposal.id, true))
            .addAction(0, context.getString(R.string.jam_vote_no), answer(context, proposal.id, false))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun answer(context: Context, id: String, yes: Boolean): PendingIntent {
        val intent = Intent(context, JamVoteReceiver::class.java).setAction(ACTION_VOTE).putExtra(EXTRA_ID, id).putExtra(EXTRA_YES, yes)
        return PendingIntent.getBroadcast(
            context,
            if (yes) 1 else 2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Receives the Yes / No buttons of [JamVoteNotifier]. */
class JamVoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jam = (context.applicationContext as LocalPlayerApp).container.jam
        if (intent.action == JamVoteNotifier.ACTION_ASK) {
            jam.requestChange(JamAction.parse(intent.getStringExtra(JamVoteNotifier.EXTRA_ACTION)) ?: return)
            return
        }
        if (intent.action != JamVoteNotifier.ACTION_VOTE) return
        val id = intent.getStringExtra(JamVoteNotifier.EXTRA_ID) ?: return
        val yes = intent.getBooleanExtra(JamVoteNotifier.EXTRA_YES, false)
        jam.vote(id, yes)
        JamVoteNotifier.cancel(context)
    }
}
