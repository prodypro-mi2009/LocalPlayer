package com.localplayer.music.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * The user's own playlist covers. A picked picture is copied once into the app's private storage as a small square
 * JPEG (centre-cropped, at most [TARGET] px), so no storage permission or URI grant is needed and a huge photo never
 * sits in memory. Every save gets a NEW file name (`<playlistId>-<time>.jpg`), so nothing has to be invalidated;
 * the old file is deleted afterwards. Nothing is uploaded anywhere.
 */
class PlaylistCoverStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "playlist_covers")
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)

    fun file(name: String): File = File(dir, name)

    /** Returns the new file name, or null when the picture could not be read. */
    suspend fun save(playlistId: Long, source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET) sample *= 2
            val decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val side = min(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val finalBitmap = if (side > TARGET) Bitmap.createScaledBitmap(square, TARGET, TARGET, true) else square
            dir.mkdirs()
            val name = "$playlistId-${System.currentTimeMillis()}.jpg"
            val temp = File(dir, "$name.tmp")
            temp.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (finalBitmap !== square) finalBitmap.recycle()
            if (square !== decoded) square.recycle()
            decoded.recycle()
            if (!temp.renameTo(File(dir, name))) {
                temp.delete()
                return@withContext null
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(name: String?) {
        if (name.isNullOrBlank()) return
        cache.remove(name)
        try {
            file(name).delete()
        } catch (e: Exception) {
            // Nothing to do: an orphan file is harmless.
        }
    }

    /** The decoded cover if it is already in memory (no I/O). */
    fun peek(name: String): Bitmap? = cache.get(name)

    suspend fun load(name: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(name) ?: try {
            BitmapFactory.decodeFile(file(name).path)?.also { cache.put(name, it) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TARGET = 800
        const val CACHE_ENTRIES = 24
    }
}
