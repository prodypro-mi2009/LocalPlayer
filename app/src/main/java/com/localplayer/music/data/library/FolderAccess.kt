package com.localplayer.music.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Storage Access Framework side of folder management: persisted permissions and walking a granted folder.
 * The app never asks for access to the whole storage; each folder is picked by the user in the system picker.
 */
class FolderAccess(private val context: Context) {

    data class SafFile(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val lastModified: Long,
        val size: Long,
        val folderKey: String?,
    )

    /** [complete] is false when some sub-folder could not be listed; the files found so far are still valid. */
    data class Walk(val files: List<SafFile>, val complete: Boolean)

    /** The folder key of a picked tree, or null when it is not an external-storage folder (Downloads provider, ...). */
    fun folderKeyOf(treeUri: Uri): String? =
        try {
            if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) null
            else FolderKeys.fromFolderDocumentId(DocumentsContract.getTreeDocumentId(treeUri))
        } catch (e: Exception) {
            null
        }

    /**
     * Keeps the permission across restarts. Read+write is requested (writing is what tag editing needs);
     * if the provider only granted read access, read is kept. Returns false when no permission could be kept.
     */
    fun persist(treeUri: Uri): Boolean {
        val resolver = context.contentResolver
        return try {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        } catch (e: Exception) {
            try {
                resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun release(treeUri: Uri) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.releasePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            try {
                resolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: Exception) {
                // Already gone: nothing to release.
            }
        }
    }

    fun hasPermission(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    fun displayName(treeUri: Uri): String {
        val key = folderKeyOf(treeUri)
        val name = key?.let { FolderKeys.nameOf(it) }.orEmpty()
        return name.ifEmpty { key?.let { FolderKeys.volumeOf(it) } ?: treeUri.lastPathSegment.orEmpty() }
    }

    /**
     * Lists every supported audio file below [treeUri] (sub-folders included). Returns null when the folder
     * itself cannot be read (permission revoked, volume gone). Nothing is read from the files here.
     */
    fun walk(treeUri: Uri, maxFiles: Int = MAX_FILES): Walk? {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return null
        }
        val files = ArrayList<SafFile>()
        var complete = true
        val pending = ArrayDeque<String>()
        pending.add(rootId)
        var first = true
        while (pending.isNotEmpty() && files.size < maxFiles) {
            val dirId = pending.removeLast()
            val folderKey = FolderKeys.fromFolderDocumentId(dirId)
            val listed = list(treeUri, dirId)
            if (listed == null) {
                if (first) return null
                complete = false
                continue
            }
            first = false
            for (entry in listed) {
                if (entry.isDirectory) {
                    pending.add(entry.id)
                } else if (MediaStoreAudio.isSupportedName(entry.name)) {
                    files.add(
                        SafFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id),
                            displayName = entry.name,
                            mimeType = entry.mime,
                            lastModified = entry.modified,
                            size = entry.size,
                            folderKey = folderKey,
                        ),
                    )
                }
            }
        }
        if (pending.isNotEmpty()) complete = false
        return Walk(files, complete)
    }

    private class Entry(val id: String, val name: String, val mime: String?, val modified: Long, val size: Long) {
        val isDirectory: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun list(treeUri: Uri, parentId: String): List<Entry>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val cursor = try {
            context.contentResolver.query(children, projection, null, null, null)
        } catch (e: Exception) {
            null
        } ?: return null
        val result = ArrayList<Entry>()
        cursor.use { c ->
            while (c.moveToNext()) {
                result.add(
                    Entry(
                        id = c.getString(0) ?: continue,
                        name = c.getString(1) ?: "",
                        mime = c.getString(2),
                        modified = if (c.isNull(3)) 0L else c.getLong(3),
                        size = if (c.isNull(4)) 0L else c.getLong(4),
                    ),
                )
            }
        }
        return result
    }

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val MAX_FILES = 100_000

        /** Every indexed file of a granted folder has a URI that starts with this. */
        fun rowPrefix(treeUri: String): String = "$treeUri/document/"
    }
}
