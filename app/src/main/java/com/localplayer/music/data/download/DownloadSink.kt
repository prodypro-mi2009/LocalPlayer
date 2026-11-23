package com.localplayer.music.data.download

import androidx.core.net.toUri
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Puts a finished MP3 where the user wants it: a folder chosen with the system picker, or (default) the public
 * Music/LocalPlayer folder through MediaStore (no storage permission needed on Android 10+).
 */
class DownloadSink(private val context: Context) {

    /** Returns true when the file was stored. [folderTreeUri] null = default location. */
    fun save(source: File, fileName: String, title: String, artist: String, album: String, folderTreeUri: String?): Boolean {
        val name = "$fileName.mp3"
        return try {
            when {
                folderTreeUri != null -> saveToTree(source, name, folderTreeUri.toUri())
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(source, name, title, artist, album)
                else -> saveToLegacyFolder(source, name)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** File names (with extension) of the files directly inside the save folder. Empty when it cannot be read. */
    fun existingNames(folderTreeUri: String?): List<String> = try {
        when {
            folderTreeUri != null -> namesInTree(folderTreeUri.toUri())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> namesInMediaStore()
            else -> File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), FOLDER_NAME).list()?.toList().orEmpty()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun namesInTree(tree: Uri): List<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<String>()
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR) out.add(c.getString(0).orEmpty())
            }
        }
        return out
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun namesInMediaStore(): List<String> {
        val out = ArrayList<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf("${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME/"),
            null,
        )?.use { c ->
            while (c.moveToNext()) out.add(c.getString(0).orEmpty())
        }
        return out
    }

    private fun saveToTree(source: File, name: String, tree: Uri): Boolean {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val target = DocumentsContract.createDocument(context.contentResolver, parent, "audio/mpeg", name) ?: return false
        return try {
            context.contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("no stream")
            true
        } catch (e: Exception) {
            // Do not leave an empty or half-written file in the user's folder.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(source: File, name: String, title: String, artist: String, album: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME")
            put(MediaStore.Audio.Media.TITLE, title)
            if (artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, artist)
            if (album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        return true
    }

    /** Android 8-9: the app's own Music folder (no broad storage permission), then asks the media scanner to list it. */
    private fun saveToLegacyFolder(source: File, name: String): Boolean {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return false, FOLDER_NAME).apply { mkdirs() }
        var target = File(dir, name)
        var n = 2
        while (target.exists()) target = File(dir, name.removeSuffix(".mp3") + " ($n).mp3").also { n++ }
        source.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("audio/mpeg"), null)
        return true
    }

    companion object {
        const val FOLDER_NAME = "LocalPlayer"
    }
}
