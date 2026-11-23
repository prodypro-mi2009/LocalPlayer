package com.localplayer.music.data.backup

import androidx.core.net.toUri
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.localplayer.music.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The two OPT-IN automatic copies of the complete backup (.lpbackup):
 *  - to a folder the user picked (any folder, also on an SD card or a cloud app that offers a folder), and
 *  - a copy kept inside the app, which Android's own backup (Google backup) carries to a new phone.
 * Nothing runs unless the user switched it on; nothing runs in the background: a due backup is made a minute after
 * the app was opened, at most once a week.
 */
class AutoBackupManager(
    private val context: Context,
    private val settings: AppSettings,
    private val backup: SettingsBackupManager,
) {
    private val copyDir get() = File(context.filesDir, "auto_backup")
    private val copyFile get() = File(copyDir, "LocalPlayer.lpbackup")

    fun folderDue(nowMs: Long): Boolean =
        !settings.autoBackupFolder.value.isNullOrEmpty() &&
            AutoBackupRules.isDue(settings.autoBackupEnabled.value, nowMs, settings.lastAutoBackupAt, settings.lastAutoBackupTryAt)

    fun androidCopyDue(nowMs: Long): Boolean =
        AutoBackupRules.isDue(settings.androidBackupCopy.value, nowMs, settings.lastAndroidCopyAt, settings.lastAndroidCopyTryAt)

    // ---- folder ---------------------------------------------------------------------------------------------------

    /** True when the app still holds write permission for [folder] (the user can revoke it in system settings). */
    fun folderUsable(folder: String): Boolean = runCatching {
        val tree = folder.toUri()
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
    }.getOrDefault(false)

    suspend fun backupToFolder(folder: String): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        settings.lastAutoBackupTryAt = now
        val result = try {
            if (!folderUsable(folder)) error("No access to the backup folder any more")
            val resolver = context.contentResolver
            val tree = folder.toUri()
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = AutoBackupRules.FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now)) + AutoBackupRules.FILE_SUFFIX
            val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                ?: error("The backup file could not be created in the folder")
            backup.export(doc, complete = true).getOrThrow()
            runCatching { prune(tree) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (result.isSuccess) settings.lastAutoBackupAt = now
        result
    }

    private fun prune(tree: Uri) {
        val resolver = context.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val byName = HashMap<String, String>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1)
                if (id != null && name != null) byName[name] = id
            }
        }
        for (name in AutoBackupRules.toDelete(byName.keys.toList())) {
            val id = byName[name] ?: continue
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    // ---- the copy that Android / Google backup carries --------------------------------------------------------------

    suspend fun writeAndroidCopy(): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        settings.lastAndroidCopyTryAt = now
        val temp = File(copyDir, "LocalPlayer.lpbackup.tmp")
        val result = try {
            // A copy that came with Android's restore must not be overwritten before the user restored or dismissed it.
            if (restoredCopy() != null) error("A restored backup copy is waiting: restore it or dismiss it first")
            copyDir.mkdirs()
            backup.export(Uri.fromFile(temp), complete = true).getOrThrow()
            if (temp.length() > AutoBackupRules.MAX_ANDROID_COPY_BYTES) error("The backup is too large for Android's own backup (20 MB)")
            if (!temp.renameTo(copyFile)) {
                temp.copyTo(copyFile, overwrite = true)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { temp.delete() }
        }
        if (result.isSuccess) settings.lastAndroidCopyAt = now
        result
    }

    /** Turning the option off removes the copy, so Android's backup no longer carries it. */
    fun removeAndroidCopy() {
        runCatching { copyFile.delete() }
        runCatching { File(copyDir, "LocalPlayer.lpbackup.tmp").delete() }
        settings.lastAndroidCopyAt = 0L
    }

    /**
     * A copy that arrived by Android's restore on this phone: the file exists but this installation never wrote it
     * (the preferences are not part of Android's backup, so the "last written" time is 0 here).
     */
    fun restoredCopy(): File? =
        copyFile.takeIf { settings.lastAndroidCopyAt == 0L && it.isFile && it.length() > 0 }

    /** Done with a restored copy (restored or dismissed): remember that this installation owns the file now. */
    fun markRestoredCopyHandled() {
        settings.lastAndroidCopyAt = System.currentTimeMillis()
    }
}
