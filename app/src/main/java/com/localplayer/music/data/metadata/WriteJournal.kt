package com.localplayer.music.data.metadata

import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

/**
 * A small "write-ahead" journal for saving tags into the user's real audio file.
 *
 * The backup of the original lives in its own folder under the app's private files (not the cache, which the system
 * may clear). Right before the real file is overwritten, a journal entry is written there with the SHA-256 of the
 * original and of the new file. If the app or the phone stops in the middle of the write, the folder survives, and the
 * next start can tell from the file's current SHA-256 whether it is complete, untouched, or damaged (then the user is
 * offered the original back). On success or on a successful automatic restore the folder is simply deleted.
 *
 * Plain Java only (no Android classes), so it is unit-tested.
 */
class WriteJournal(private val root: File) {

    data class Entry(
        val id: String,
        val uri: String,
        val name: String,
        val backupSha: String,
        val newSha: String,
        val startedAt: Long,
    )

    enum class Verdict {
        /** The file already holds the new content: the write finished. */
        COMPLETED,

        /** The file still holds the original: nothing happened. */
        UNCHANGED,

        /** The file matches neither and was changed long after the save began: someone else replaced it. */
        CHANGED_LATER,

        /** The file matches neither and was last written around the save: the write was cut off. */
        DAMAGED,
    }

    /** A new, empty work folder for one save. It becomes a recovery folder once [begin] is called. */
    fun newDir(): File = File(root, UUID.randomUUID().toString()).apply { mkdirs() }

    /** Writes the journal entry (to a temp file, forced to storage, then renamed) into [dir]. Call right before the real write. */
    fun begin(dir: File, uri: String, name: String, backupSha: String, newSha: String, now: Long = System.currentTimeMillis()) {
        val props = Properties().apply {
            setProperty("uri", uri)
            setProperty("name", name)
            setProperty("backupSha", backupSha)
            setProperty("newSha", newSha)
            setProperty("startedAt", now.toString())
        }
        val tmp = File(dir, "$JOURNAL.tmp")
        FileOutputStream(tmp).use { out ->
            props.store(out, null)
            out.flush()
            out.fd.sync()
        }
        val target = File(dir, JOURNAL)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun backupFile(id: String): File = File(File(root, id), BACKUP)

    /** All readable journal entries. A folder without a valid journal is not listed here. */
    fun pending(): List<Entry> =
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }.orEmpty()

    fun find(id: String): Entry? = read(File(root, id))

    /** The save is over (success, or the original was put back): the folder is no longer needed. */
    fun finish(id: String) {
        File(root, id).deleteRecursively()
    }

    /**
     * After a save of [uri] succeeded, older recovery folders of the same file are obsolete (they hold an older
     * original than what the file now legitimately is). The folder [exceptId] (the current save) is left alone.
     */
    fun finishOthers(uri: String, exceptId: String) {
        pending().filter { it.uri == uri && it.id != exceptId }.forEach { finish(it.id) }
    }

    /**
     * The [max] newest of [entries] (by start time), newest first. The others are NOT deleted: a recovery folder holds
     * the only copy of an original whose file is damaged, so it stays until the person has dealt with it (the next
     * ones are offered afterwards).
     */
    fun keepNewest(entries: List<Entry>, max: Int = MAX_KEPT): List<Entry> =
        entries.sortedByDescending { it.startedAt }.take(max)

    /**
     * Folders without a journal belong to a save that stopped before the real file was touched, so they hold nothing
     * to recover. Only old ones are removed, so a save that is running right now is never disturbed.
     */
    fun cleanOrphans(now: Long = System.currentTimeMillis(), olderThanMs: Long = 10 * 60 * 1000L) {
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (!File(dir, JOURNAL).isFile && now - dir.lastModified() > olderThanMs) dir.deleteRecursively()
        }
    }

    private fun read(dir: File): Entry? {
        val file = File(dir, JOURNAL)
        if (!file.isFile) return null
        return try {
            val p = Properties().apply { file.inputStream().use { load(it) } }
            Entry(
                id = dir.name,
                uri = p.getProperty("uri") ?: return null,
                name = p.getProperty("name").orEmpty(),
                backupSha = p.getProperty("backupSha") ?: return null,
                newSha = p.getProperty("newSha") ?: return null,
                startedAt = p.getProperty("startedAt")?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val JOURNAL = "journal.properties"
        const val BACKUP = "backup.bin"

        /** How many unresolved (damaged) recovery copies are offered at once; the others wait on disk. */
        const val MAX_KEPT = 5

        /** What the file's current SHA-256 says about an interrupted write. */
        fun classify(currentSha: String, entry: Entry, modifiedAt: Long? = null): Verdict = when {
            currentSha == entry.newSha -> Verdict.COMPLETED
            currentSha == entry.backupSha -> Verdict.UNCHANGED
            // A cut-off write was last touched at the time of the save. A file modified much later was replaced by
            // someone else (copied from a PC, saved again elsewhere) and must not get the old original written over it.
            // Without a usable modification time the file is treated as damaged, as before.
            modifiedAt != null && modifiedAt > 0 && modifiedAt > entry.startedAt + CHANGED_LATER_AFTER_MS -> Verdict.CHANGED_LATER
            else -> Verdict.DAMAGED
        }

        /** How long after the start of a save a file may still have been touched by that save itself. */
        const val CHANGED_LATER_AFTER_MS = 5 * 60 * 1000L
    }
}
