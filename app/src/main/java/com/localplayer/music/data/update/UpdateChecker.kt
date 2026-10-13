package com.localplayer.music.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    /** Lower-case hex SHA-256 when GitHub published a digest for the asset, else null. */
    val apkSha256: String?,
)

sealed interface UpdateCheck {
    data class Newer(val release: ReleaseInfo) : UpdateCheck
    data class UpToDate(val latestTag: String) : UpdateCheck
    data object NotConfigured : UpdateCheck
    data object NoRelease : UpdateCheck
    data object Offline : UpdateCheck
    data object RateLimited : UpdateCheck
    data class Failed(val code: Int) : UpdateCheck
}

sealed interface DownloadResult {
    data class Done(val file: File) : DownloadResult
    data object Failed : DownloadResult
    data object Corrupt : DownloadResult
    data object NotAllowed : DownloadResult
}

/**
 * Looks for a newer release in the project's PUBLIC GitHub Releases (HTTPS, no token, no login) and downloads the
 * release APK. Nothing is installed here: Android's own installer asks the user to confirm, and only accepts an
 * APK signed with the same key as the installed app.
 */
class UpdateChecker(private val userAgent: String) {

    suspend fun check(repo: String, currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        if (!UpdateRules.isValidRepo(repo)) return@withContext UpdateCheck.NotConfigured
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = parseRelease(body) ?: return@withContext UpdateCheck.NoRelease
                    if (VersionCompare.isNewer(release.tag, currentVersion)) UpdateCheck.Newer(release)
                    else UpdateCheck.UpToDate(release.tag)
                }
                404 -> UpdateCheck.NoRelease
                403, 429 -> UpdateCheck.RateLimited
                else -> UpdateCheck.Failed(code)
            }
        } catch (e: Exception) {
            UpdateCheck.Offline
        } finally {
            connection?.disconnect()
        }
    }

    fun parseRelease(body: String): ReleaseInfo? = try {
        val o = JSONObject(body)
        val tag = o.optString("tag_name").takeIf { it.isNotEmpty() } ?: return null
        val assets = o.optJSONArray("assets")
        var name: String? = null; var url: String? = null; var size = 0L; var sha: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val n = a.optString("name")
                if (n.endsWith(".apk", ignoreCase = true) && !n.contains("debug", ignoreCase = true)) {
                    name = n; url = a.optString("browser_download_url").takeIf { it.isNotEmpty() }
                    size = a.optLong("size", 0L)
                    sha = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
                    break
                }
            }
        }
        ReleaseInfo(
            tag = tag,
            title = o.optString("name").ifEmpty { tag },
            notes = o.optString("body"),
            pageUrl = o.optString("html_url"),
            apkName = name, apkUrl = url, apkSize = size, apkSha256 = sha,
        )
    } catch (e: Exception) {
        null
    }

    /** Streams the APK into [target], reporting 0..1 progress, and verifies size and (when published) SHA-256. */
    suspend fun download(release: ReleaseInfo, target: File, onProgress: (Float) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: return@withContext DownloadResult.Failed
        if (!UpdateRules.isAllowedDownloadUrl(url)) return@withContext DownloadResult.NotAllowed
        var connection: HttpURLConnection? = null
        try {
            target.parentFile?.mkdirs()
            target.delete()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
            }
            // GitHub redirects release downloads to its own CDN over HTTPS; refuse anything that is not HTTPS.
            if (connection.responseCode != 200) return@withContext DownloadResult.Failed
            if (connection.url.protocol != "https") return@withContext DownloadResult.NotAllowed
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) { target.delete(); return@withContext DownloadResult.Corrupt }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.apkSha256 != null && !release.apkSha256.equals(hex, ignoreCase = true)) {
                target.delete(); return@withContext DownloadResult.Corrupt
            }
            DownloadResult.Done(target)
        } catch (e: Exception) {
            target.delete()
            DownloadResult.Failed
        } finally {
            connection?.disconnect()
        }
    }
}
