package com.localplayer.music.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.localplayer.music.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * The user's own background picture. The picked image is copied once into the app's private storage as a
 * downsampled JPEG (so no storage permission or persistent URI grant is needed and a huge photo never sits in
 * memory); nothing is uploaded anywhere. Kept strictly separate from album artwork and artwork colors.
 */
class BackgroundRepository(private val context: Context, private val settings: AppSettings) {
    private val file get() = File(context.filesDir, "background.jpg")

    /** Returns false if the picture could not be read. */
    suspend fun set(source: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            if (!temp.renameTo(file)) return@withContext false
            settings.setBackgroundVersion(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The stored picture file for a backup, or null when there is no custom background. */
    fun fileForBackup(): File? = file.takeIf { it.isFile && it.length() > 0 }

    /** Restores a picture from a verified backup file (it must decode as an image). Returns false and changes nothing otherwise. */
    suspend fun restoreFrom(source: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
            val temp = File(file.path + ".tmp")
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(file)) { temp.delete(); return@withContext false }
            settings.setBackgroundVersion(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun reset() {
        file.delete()
        settings.setBackgroundVersion(0L)
    }

    /** The picture, already small enough to keep in memory, or null. */
    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) null else BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
