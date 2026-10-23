package com.localplayer.music.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Puts a finished MP3 where the user wants it: a folder chosen with the system picker, or (default) the public
 * Music/LocalPlayer folder through MediaStore (no storage permission needed on Android 10+).
 */
class DownloadSink(private val context: Context) {

    /** Returns true when the file was stored. [folderTreeUri] null = default location. */
    fun save(source: File, fileName: String, title: String, artist: String, folderTreeUri: String?): Boolean {
        val name = "$fileName.mp3"
        return try {
            when {
                folderTreeUri != null -> saveToTree(source, name, Uri.parse(folderTreeUri))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(source, name, title, artist)
                else -> saveToLegacyFolder(source, name)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToTree(source: File, name: String, tree: Uri): Boolean {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val target = DocumentsContract.createDocument(context.contentResolver, parent, "audio/mpeg", name) ?: return false
        context.contentResolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }

    private fun saveToMediaStore(source: File, name: String, title: String, artist: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME")
            put(MediaStore.Audio.Media.TITLE, title)
            if (artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, artist)
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
