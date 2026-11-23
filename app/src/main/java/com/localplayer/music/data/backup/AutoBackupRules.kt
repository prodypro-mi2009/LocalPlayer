package com.localplayer.music.data.backup

/** When an automatic backup is due. Pure, so it is tested on the JVM. */
object AutoBackupRules {
    const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    /** After a failed attempt, wait this long before trying again (so a lost folder permission does not retry at every launch). */
    const val RETRY_MS = 12L * 60 * 60 * 1000
    const val KEEP_FILES = 3
    const val MAX_ANDROID_COPY_BYTES = 20L * 1024 * 1024
    const val FILE_PREFIX = "LocalPlayer-auto-"
    const val FILE_SUFFIX = ".lpbackup"

    fun isDue(enabled: Boolean, nowMs: Long, lastOkMs: Long, lastTryMs: Long): Boolean {
        if (!enabled) return false
        val dueByAge = lastOkMs <= 0L || nowMs < lastOkMs || nowMs - lastOkMs >= INTERVAL_MS
        val retryOk = lastTryMs <= 0L || nowMs < lastTryMs || nowMs - lastTryMs >= RETRY_MS
        return dueByAge && retryOk
    }

    /** The files of the auto-backup folder to delete: everything of ours beyond the newest [keep] (names carry the date). */
    fun toDelete(names: List<String>, keep: Int = KEEP_FILES): List<String> =
        names.filter { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }.sortedDescending().drop(keep)
}
