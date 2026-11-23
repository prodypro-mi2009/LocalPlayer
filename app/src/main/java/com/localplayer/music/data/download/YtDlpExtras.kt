package com.localplayer.music.data.download

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File

/**
 * Optional helpers for when YouTube answers "Sign in to confirm you're not a bot". All of them are OFF until the user
 * turns them on in the download screen, and nothing here runs at app start: the settings are only read when a link is
 * read, a download starts or the screen is opened.
 */
object YtDlpExtras {
    private const val PREFS = "ytdlp_extras"
    private const val KEY_NIGHTLY = "nightly"
    private const val KEY_FALLBACK = "fallback_clients"
    private const val KEY_COOKIES = "use_cookies"
    private const val KEY_CHANNEL = "installed_channel"
    private const val MAX_COOKIE_BYTES = 2_000_000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nightly(context: Context) = prefs(context).getBoolean(KEY_NIGHTLY, false)

    fun fallbackClients(context: Context) = prefs(context).getBoolean(KEY_FALLBACK, false)

    fun cookiesEnabled(context: Context) = prefs(context).getBoolean(KEY_COOKIES, false)

    fun setNightly(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_NIGHTLY, value) }

    fun setFallbackClients(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_FALLBACK, value) }

    fun setCookiesEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_COOKIES, value) }

    /** "nightly" or "stable": the channel of the last successful yt-dlp update (null = never updated). */
    fun installedChannel(context: Context): String? = prefs(context).getString(KEY_CHANNEL, null)

    fun setInstalledChannel(context: Context, channel: String) = prefs(context).edit { putString(KEY_CHANNEL, channel) }

    /** The channel to show for [version]: the recorded one, otherwise guessed (nightly tags carry a time: 2026.11.16.123456). */
    fun channelFor(context: Context, version: String?): String? {
        if (version == null) return null
        return installedChannel(context) ?: if (version.count { it == '.' } >= 3) "nightly" else "stable"
    }

    /** The private copy of the cookies file (inside the app's own storage, never shared). */
    private fun cookiesFile(context: Context) = File(context.applicationContext.filesDir, "yt-cookies.txt")

    fun hasCookies(context: Context): Boolean = cookiesFile(context).let { it.isFile && it.length() > 0 }

    /** The yt-dlp options that hand over the cookies; empty unless the user turned them on and chose a file. */
    fun cookieOptions(context: Context): List<Pair<String, String>> =
        if (cookiesEnabled(context) && hasCookies(context)) listOf("--cookies" to cookiesFile(context).absolutePath) else emptyList()

    /** Copies the chosen cookies.txt (Netscape format) into the app. False when it does not look like one. */
    fun importCookies(context: Context, uri: Uri): Boolean = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it) } ?: ByteArray(0)
        val text = if (bytes.size > MAX_COOKIE_BYTES) "" else bytes.toString(Charsets.UTF_8)
        if (!looksLikeCookies(text)) {
            false
        } else {
            val target = cookiesFile(context)
            val part = File(target.parentFile, target.name + ".part")
            part.writeBytes(bytes)
            part.renameTo(target)
        }
    } catch (e: Exception) {
        false
    }

    /** Reads at most MAX_COOKIE_BYTES + 1 bytes (one more than allowed is how "too big" is noticed). */
    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() <= MAX_COOKIE_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun clearCookies(context: Context) {
        cookiesFile(context).delete()
    }

    /** A Netscape cookie file has lines of 7 tab-separated fields (comments start with #). */
    internal fun looksLikeCookies(text: String): Boolean =
        text.lineSequence().any { line ->
            val clean = line.removePrefix("#HttpOnly_")
            !clean.startsWith("#") && clean.split('\t').size >= 7
        }
}
