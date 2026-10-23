package com.localplayer.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.MainActivity
import com.localplayer.music.R
import com.localplayer.music.playback.MediaItemFactory
import com.localplayer.music.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Home-screen widgets (2x1 and 2x2), driven by the EXISTING player of the playback service (no second player).
 *
 * Event driven: a Player.Listener is attached ONLY while widgets are enabled, at least one widget is placed and the
 * service is running; it coalesces events into one update after a short delay. There is no polling, no periodic
 * worker and no progress ticker. When widgets are disabled (the default) nothing is attached, nothing is decoded and
 * every placed widget is shown as an inactive tile without controls.
 */
object WidgetController {
    private val main = Handler(Looper.getMainLooper())
    private var player: Player? = null
    private var listener: Player.Listener? = null
    private var scope: CoroutineScope? = null
    private var artJob: Job? = null
    private var artKey: String? = null
    private var art: Bitmap? = null
    private var appContext: Context? = null
    private val refresh = Runnable { render() }

    private val providers = listOf(CompactWidgetProvider::class.java, ExpandedWidgetProvider::class.java)

    /** The service hands over / takes back its player (no work is done here besides storing the reference). */
    fun setPlayer(context: Context, p: Player?) {
        appContext = context.applicationContext
        if (p == null) detach()          // remove the listener from the OLD player before forgetting it
        player = p
        // Widgets off (the default): nothing else happens at service start, not even a widget lookup.
        if (p == null || enabled(context.applicationContext)) sync()
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return providers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    private fun enabled(context: Context) = (context.applicationContext as LocalPlayerApp).container.settings.widgetsEnabledNow()

    /** Brings listener / artwork state in line with: widgets enabled + a widget placed + player available. */
    fun sync(context: Context? = null) {
        val ctx = (context ?: appContext)?.applicationContext ?: return
        appContext = ctx
        val shouldListen = player != null && enabled(ctx) && hasWidgets(ctx)
        if (shouldListen && listener == null) attach() else if (!shouldListen && listener != null) detach()
        render()
    }

    private fun attach() {
        val p = player ?: return
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                    )
                ) schedule()
            }
        }
        p.addListener(l)
        listener = l
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private fun detach() {
        listener?.let { player?.removeListener(it) }
        listener = null
        main.removeCallbacks(refresh)
        artJob?.cancel(); artJob = null
        scope?.cancel(); scope = null
        art = null; artKey = null
    }

    private fun schedule() {
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, 150)
    }

    /** Renders every placed widget for the current state (inactive tile / idle / playing). */
    fun render() {
        val ctx = appContext ?: return
        val manager = AppWidgetManager.getInstance(ctx)
        val isEnabled = enabled(ctx)
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) continue
            val expanded = cls == ExpandedWidgetProvider::class.java
            val views = if (!isEnabled) inactive(ctx) else active(ctx, expanded)
            manager.updateAppWidget(ids, views)
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Widgets turned off in the app: no controls, just a hint. Android cannot remove a pinned widget by itself. */
    private fun inactive(ctx: Context): RemoteViews = RemoteViews(ctx.packageName, R.layout.widget_inactive).apply {
        setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_inactive))
        setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
    }

    private fun mediaKey(ctx: Context, code: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(ctx, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
        return PendingIntent.getForegroundService(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun active(ctx: Context, expanded: Boolean): RemoteViews {
        val views = RemoteViews(ctx.packageName, if (expanded) R.layout.widget_expanded else R.layout.widget_compact)
        val p = player
        val item = p?.currentMediaItem
        views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        if (p == null || item == null) {
            // Nothing is loaded (or the service is not running): show the app name, the play button opens the app.
            views.setTextViewText(R.id.widget_title, ctx.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_artist, ctx.getString(R.string.widget_nothing_playing))
            views.setImageViewResource(R.id.widget_play, R.drawable.ic_widget_play)
            views.setOnClickPendingIntent(R.id.widget_play, openApp(ctx))
            views.setOnClickPendingIntent(R.id.widget_prev, openApp(ctx))
            views.setOnClickPendingIntent(R.id.widget_next, openApp(ctx))
            return views
        }
        val md = item.mediaMetadata
        views.setTextViewText(R.id.widget_title, md.title ?: "")
        views.setTextViewText(R.id.widget_artist, md.artist ?: "")
        views.setImageViewResource(R.id.widget_play, if (p.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        views.setOnClickPendingIntent(R.id.widget_play, mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_prev, mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_next, mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))

        // Artwork: the app's own cached, downsampled bitmap, loaded once per track (never the full-size picture).
        val ref = MediaItemFactory.artworkRef(item)
        val key = ref?.let { "${it.trackId}_${it.lastModified}" }
        if (key != null && key == artKey && art != null) {
            views.setImageViewBitmap(R.id.widget_art, art)
        } else if (ref != null && key != artKey) {
            artKey = key; art = null
            artJob?.cancel()
            val container = (ctx.applicationContext as LocalPlayerApp).container
            artJob = scope?.launch {
                val bmp = container.artwork.load(ref.trackId, ref.sourceUri, ref.lastModified, if (expanded) 256 else 128)
                if (bmp != null && artKey == key) { art = bmp; schedule() }
            }
        }
        return views
    }
}
