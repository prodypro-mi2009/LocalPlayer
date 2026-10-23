package com.localplayer.music.data.update

/**
 * Pure version comparison for release tags such as "v1.0.1", "1.0.0" or "1.0.0-phase4" (a suffix means pre-release).
 * Trailing LETTERS directly after the last number ("1.0.9ap") are a build label and are ignored for ordering.
 */
object VersionCompare {
    private class Parsed(val numbers: List<Int>, val preRelease: String?)

    private fun parse(text: String): Parsed? {
        val core = text.trim().removePrefix("v").removePrefix("V")
        if (core.isEmpty() || !core[0].isDigit()) return null
        val cut = core.indexOfFirst { it == '-' || it == '+' || it == ' ' }
        val numbersPart = if (cut >= 0) core.substring(0, cut) else core
        val suffix = if (cut >= 0 && core[cut] == '-') core.substring(cut + 1).ifEmpty { null } else null
        // Trailing letters are a build label, ignored for ordering ("1.0.9ap" is 1.0.9).
        val digitsPart = numbersPart.trimEnd { it.isLetter() }
        if (digitsPart.isEmpty() || !digitsPart.last().isDigit()) return null
        val numbers = digitsPart.split('.').map { it.toIntOrNull() ?: return null }
        return Parsed(numbers, suffix)
    }

    /** True when [text] is a readable version tag such as "1.0.5" or "v1.0.5" (anything else is ignored as malformed). */
    fun isValid(text: String): Boolean = parse(text) != null

    /** True only when [latest] is certainly newer than [current]; unreadable versions are never "newer". */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        val size = maxOf(l.numbers.size, c.numbers.size)
        for (i in 0 until size) {
            val a = l.numbers.getOrElse(i) { 0 }
            val b = c.numbers.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        // Same numbers: the final release is newer than a pre-release of it.
        return l.preRelease == null && c.preRelease != null
    }
}

/** Pure safety rules for the update flow. */
object UpdateRules {
    /** "owner/name" with only the characters GitHub allows. */
    fun isValidRepo(repo: String) = Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""").matches(repo)

    /** Release assets are served from github.com release downloads; nothing else is downloaded. */
    fun isAllowedDownloadUrl(url: String): Boolean =
        Regex("""https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/.+""").matches(url)

    /** Same, but only for the one configured repository (GitHub names are case-insensitive). */
    fun isAllowedDownloadUrl(url: String, repo: String): Boolean =
        isValidRepo(repo) && url.startsWith("https://github.com/$repo/releases/download/", ignoreCase = true) &&
            isAllowedDownloadUrl(url)

    /** The release page must be on github.com under the configured repository's /releases/. */
    fun isAllowedReleasePage(url: String, repo: String): Boolean =
        isValidRepo(repo) && Regex("""https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/.+""").matches(url) &&
            url.startsWith("https://github.com/$repo/releases/", ignoreCase = true)

    class Asset(val name: String, val url: String, val size: Long, val digest: String)

    /**
     * The APK of a release: only *.apk files, never debug/unsigned builds; the one named after the app wins, then
     * a "release" build, then the first remaining APK.
     */
    fun pickApk(assets: List<Asset>): Asset? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) &&
                !it.name.contains("debug", ignoreCase = true) && !it.name.contains("unsigned", ignoreCase = true)
        }
        return apks.firstOrNull { it.name.contains("localplayer", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.contains("release", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Periodic check: only when enabled, a repository is configured, and at least a day passed since the last check. */
    fun shouldAutoCheck(enabled: Boolean, repoConfigured: Boolean, nowMs: Long, lastCheckMs: Long): Boolean =
        enabled && repoConfigured && (lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= AUTO_CHECK_INTERVAL_MS)
}
