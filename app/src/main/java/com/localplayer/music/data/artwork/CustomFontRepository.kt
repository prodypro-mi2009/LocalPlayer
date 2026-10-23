package com.localplayer.music.data.artwork

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.localplayer.music.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A font file the user imported (for example NDot, which cannot be redistributed inside this app). It is copied
 * once into the app's private storage; nothing leaves the device. A file that Android cannot load as a font is
 * rejected and never stored.
 */
class CustomFontRepository(private val context: Context, private val settings: AppSettings) {
    val file: File get() = File(context.filesDir, "custom-font.ttf")

    fun exists(): Boolean = file.isFile && file.length() > 0

    /** Returns false when the file is too big, unreadable or not a usable font. */
    suspend fun import(source: Uri): Boolean = withContext(Dispatchers.IO) {
        val temp = File(file.path + ".tmp")
        try {
            var copied = 0L
            val input = context.contentResolver.openInputStream(source) ?: return@withContext false
            input.use { i ->
                temp.outputStream().use { o ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_BYTES) return@withContext false.also { temp.delete() }
                        o.write(buffer, 0, n)
                    }
                }
            }
            // Typeface.Builder returns null when the file is not a font Android can load.
            if (copied == 0L || Typeface.Builder(temp).build() == null) {
                temp.delete()
                return@withContext false
            }
            if (!temp.renameTo(file)) {
                temp.delete()
                return@withContext false
            }
            settings.setCustomFontVersion(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    /** The imported font file for a backup, or null. */
    fun fileForBackup(): File? = file.takeIf { exists() }

    /** Restores a font from a verified backup file; it must load as a font. Returns false and changes nothing otherwise. */
    suspend fun restoreFrom(source: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (source.length() == 0L || source.length() > MAX_BYTES || Typeface.Builder(source).build() == null) return@withContext false
            val temp = File(file.path + ".tmp")
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(file)) { temp.delete(); return@withContext false }
            settings.setCustomFontVersion(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun remove() {
        file.delete()
        settings.setCustomFontVersion(0L)
    }

    private companion object {
        const val MAX_BYTES = 16L * 1024 * 1024
    }
}
