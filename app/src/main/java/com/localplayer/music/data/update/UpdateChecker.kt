package com.localplayer.music.data.update

import com.localplayer.music.util.toLowerHex
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

    companion object {
        /** No real release APK of this app comes close to this; it only bounds a stream whose size is unknown. */
        const val MAX_APK_BYTES = 300L * 1024 * 1024

        /** True when a 403 reply is GitHub's rate limit: no requests remaining, or a Retry-After is given. */
        fun isRateLimit(remaining: String?, retryAfter: String?): Boolean =
            remaining?.trim() == "0" || !retryAfter.isNullOrBlank()
    }

    suspend fun check(repo: String, currentVersion: String, includeBeta: Boolean = false): UpdateCheck = withContext(Dispatchers.IO) {
        if (!UpdateRules.isValidRepo(repo)) return@withContext UpdateCheck.NotConfigured
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(if (includeBeta) "https://api.github.com/repos/$repo/releases?per_page=15" else "https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = (if (includeBeta) parseBestRelease(body, repo) else parseRelease(body, repo))
                        ?: return@withContext UpdateCheck.NoRelease
                    if (VersionCompare.isNewer(release.tag, currentVersion)) UpdateCheck.Newer(release)
                    else UpdateCheck.UpToDate(release.tag)
                }
                404 -> UpdateCheck.NoRelease
                429 -> UpdateCheck.RateLimited
                // GitHub answers 403 for an exhausted rate limit, but also for other refusals: only the first is "rate limited".
                403 -> if (isRateLimit(connection.getHeaderField("X-RateLimit-Remaining"), connection.getHeaderField("Retry-After"))) {
                    UpdateCheck.RateLimited
                } else {
                    UpdateCheck.Failed(code)
                }
                else -> UpdateCheck.Failed(code)
            }
        } catch (e: java.io.IOException) {
            // No network, DNS failure, timeout, broken TLS: "offline". Anything else below is a real failure.
            UpdateCheck.Offline
        } catch (e: Exception) {
            UpdateCheck.Failed(0)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Reads the release JSON. Drafts, pre-releases and unreadable tags give null (= treated as "no release"). The
     * page and APK links are kept only when they belong to the configured repository on github.com.
     */
    fun parseRelease(body: String, repo: String, allowPreRelease: Boolean = false): ReleaseInfo? {
        val o = try {
            JSONObject(body)
        } catch (e: Exception) {
            return null
        }
        return parseReleaseObject(o, repo, allowPreRelease)
    }

    /** From a releases LIST (beta channel): the highest readable version among published releases, pre-releases included. */
    fun parseBestRelease(body: String, repo: String): ReleaseInfo? {
        val arr = try {
            org.json.JSONArray(body)
        } catch (e: Exception) {
            return null
        }
        var best: ReleaseInfo? = null
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i)?.let { parseReleaseObject(it, repo, allowPreRelease = true) } ?: continue
            if (best == null || VersionCompare.isNewer(r.tag, best.tag)) best = r
        }
        return best
    }

    private fun parseReleaseObject(o: JSONObject, repo: String, allowPreRelease: Boolean): ReleaseInfo? {
        if (o.optBoolean("draft", false) || (!allowPreRelease && o.optBoolean("prerelease", false))) return null
        val tag = o.optString("tag_name").takeIf { VersionCompare.isValid(it) } ?: return null
        val list = ArrayList<UpdateRules.Asset>()
        o.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val url = a.optString("browser_download_url")
                if (!UpdateRules.isAllowedDownloadUrl(url, repo)) continue
                list += UpdateRules.Asset(a.optString("name"), url, a.optLong("size", 0L), a.optString("digest"))
            }
        }
        val apk = UpdateRules.pickApk(list)
        return ReleaseInfo(
            tag = tag,
            title = o.optString("name").ifEmpty { tag },
            notes = o.optString("body"),
            pageUrl = o.optString("html_url").takeIf { UpdateRules.isAllowedReleasePage(it, repo) }
                ?: "https://github.com/$repo/releases",
            apkName = apk?.name, apkUrl = apk?.url, apkSize = apk?.size ?: 0L,
            apkSha256 = apk?.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
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
            if (total > MAX_APK_BYTES) return@withContext DownloadResult.Failed
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
                        // Without a known size the stream could be endless: stop at a generous cap.
                        if (done > MAX_APK_BYTES) { target.delete(); return@withContext DownloadResult.Failed }
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) { target.delete(); return@withContext DownloadResult.Corrupt }
            val hex = digest.digest().toLowerHex()
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
