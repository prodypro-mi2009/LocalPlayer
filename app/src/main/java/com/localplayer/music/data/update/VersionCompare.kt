package com.localplayer.music.data.update

/** Pure version comparison for release tags such as "v1.0.1", "1.0.0" or "1.0.0-phase4" (a suffix means pre-release). */
object VersionCompare {
    private class Parsed(val numbers: List<Int>, val preRelease: String?)

    private fun parse(text: String): Parsed? {
        val core = text.trim().removePrefix("v").removePrefix("V")
        if (core.isEmpty() || !core[0].isDigit()) return null
        val cut = core.indexOfFirst { it == '-' || it == '+' || it == ' ' }
        val numbersPart = if (cut >= 0) core.substring(0, cut) else core
        val suffix = if (cut >= 0 && core[cut] == '-') core.substring(cut + 1).ifEmpty { null } else null
        val numbers = numbersPart.split('.').map { it.toIntOrNull() ?: return null }
        return Parsed(numbers, suffix)
    }

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

    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Periodic check: only when enabled, a repository is configured, and at least a day passed since the last check. */
    fun shouldAutoCheck(enabled: Boolean, repoConfigured: Boolean, nowMs: Long, lastCheckMs: Long): Boolean =
        enabled && repoConfigured && (lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= AUTO_CHECK_INTERVAL_MS)
}
