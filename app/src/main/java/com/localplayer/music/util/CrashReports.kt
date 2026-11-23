package com.localplayer.music.util

import android.content.Context
import android.os.Build
import com.localplayer.music.data.update.AppInfo
import java.io.File

/**
 * OPT-IN crash reports, for the user to share when something goes wrong. Off by default.
 *
 * When switched on (Settings > About), an uncaught exception is written to a text file inside the app's private
 * storage (stack trace, app version, Android version, device model) before Android's normal crash handling continues.
 * Nothing is ever sent anywhere by the app: the user decides whether to share the text.
 * Whether it is on is the existence of one marker file, so a normal start costs a single File.exists() and no
 * settings are read. Switching it off deletes the marker and the saved reports.
 */
object CrashReports {
    private const val MAX_BYTES = 100_000
    private const val KEEP_CHARS = 60_000
    private var installed = false

    private fun marker(c: Context) = File(c.filesDir, "crash_reports_on")
    private fun dir(c: Context) = File(c.filesDir, "crash_reports")
    private fun file(c: Context) = File(dir(c), "reports.txt")

    fun isEnabled(c: Context): Boolean = marker(c).exists()

    fun installIfEnabled(c: Context) {
        if (marker(c).exists()) install(c.applicationContext)
    }

    fun setEnabled(c: Context, on: Boolean) {
        val app = c.applicationContext
        if (on) {
            runCatching { marker(app).createNewFile() }
            install(app)
        } else {
            runCatching { marker(app).delete() }
            clear(app)
        }
    }

    fun read(c: Context): String? = runCatching { file(c).takeIf { it.isFile && it.length() > 0 }?.readText() }.getOrNull()

    fun clear(c: Context) {
        runCatching { dir(c).deleteRecursively() }
    }

    @Synchronized
    private fun install(app: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (marker(app).exists()) write(app, thread, error)
            } catch (_: Throwable) {
                // Reporting must never get in the way of the normal crash handling.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(app: Context, thread: Thread, error: Throwable) {
        val text = buildString {
            append("=== ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())).append(" ===\n")
            append("App ").append(AppInfo.versionName(app)).append(" | Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(") | ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append(error.stackTraceToString().take(20_000)).append("\n\n")
        }
        dir(app).mkdirs()
        val f = file(app)
        var all = (if (f.isFile) f.readText() else "") + text
        if (all.length > MAX_BYTES) all = all.takeLast(KEEP_CHARS)
        f.writeText(all)
    }
}
