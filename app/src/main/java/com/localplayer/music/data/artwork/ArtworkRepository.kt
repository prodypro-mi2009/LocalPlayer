package com.localplayer.music.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Loads embedded album art as downsampled bitmaps.
 *
 * - Sizes are rounded up to a power of two (64..1024 px) so one decoded bitmap serves many requests.
 * - A small memory cache (bounded by the app heap) sits in front of a JPEG thumbnail cache in cacheDir.
 * - Original images are never kept in memory at full resolution.
 */
class ArtworkRepository(private val context: Context) {

    private val diskDir = File(context.cacheDir, "artwork").apply { mkdirs() }
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(2)
    /** Keys whose file was read fine and really has no (decodable) embedded picture. The key includes lastModified. */
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Keys whose read FAILED (I/O error, file busy, ...): retried with a bounded backoff, never forever. */
    private val failures = ConcurrentHashMap<String, ArtworkRetryPolicy.Failure>()

    /**
     * Called (off the main thread) when a track's file was read successfully and has no embedded
     * artwork although the index said it might. Lets the library stop treating that track as a
     * source of group artwork. Only a definitive "no picture" is reported, never a read error.
     */
    @Volatile
    var onDefinitiveMiss: ((trackId: Long) -> Unit)? = null

    private sealed interface Extracted {
        class Found(val bitmap: Bitmap) : Extracted
        data object None : Extracted
        data object Error : Extracted
    }

    private val memoryCache: LruCache<String, Bitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
            .coerceIn(4 * 1024, 24 * 1024)
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    /** Synchronous memory-cache lookup so list rows can avoid a placeholder flash. */
    fun peek(trackId: Long, lastModified: Long, targetPx: Int): Bitmap? =
        memoryCache.get(cacheKey(trackId, lastModified, bucketFor(targetPx)))

    suspend fun load(trackId: Long, sourceUri: String, lastModified: Long, targetPx: Int): Bitmap? {
        val bucket = bucketFor(targetPx)
        val key = cacheKey(trackId, lastModified, bucket)
        memoryCache.get(key)?.let { return it }
        if (key in missing) return null
        val previous = failures[key]
        if (previous != null && !ArtworkRetryPolicy.mayRetry(previous, System.currentTimeMillis())) return null

        return withContext(ioDispatcher) {
            memoryCache.get(key)?.let { return@withContext it }
            val file = File(diskDir, "$key.jpg")
            var bitmap: Bitmap? = if (file.exists()) decodeFile(file, bucket) else null
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
                failures.remove(key)
                return@withContext bitmap
            }
            when (val result = extract(Uri.parse(sourceUri), bucket)) {
                is Extracted.Found -> {
                    bitmap = result.bitmap
                    writeFile(file, bitmap)
                    memoryCache.put(key, bitmap)
                    failures.remove(key)
                }
                Extracted.None -> {
                    if (missing.size > MAX_TRACKED) missing.clear()
                    missing.add(key)
                    failures.remove(key)
                    onDefinitiveMiss?.invoke(trackId)
                }
                Extracted.Error -> {
                    if (failures.size > MAX_TRACKED) failures.clear()
                    failures[key] = ArtworkRetryPolicy.after(previous, System.currentTimeMillis())
                }
            }
            bitmap
        }
    }

    /** Keeps the on-disk thumbnail cache under a size limit by deleting the oldest files. */
    suspend fun trimDisk(maxBytes: Long = 64L * 1024 * 1024) = withContext(Dispatchers.IO) {
        val files = diskDir.listFiles() ?: return@withContext
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return@withContext
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun cacheKey(trackId: Long, lastModified: Long, bucket: Int) = "${trackId}_${lastModified}_$bucket"

    private fun bucketFor(px: Int): Int {
        var bucket = 64
        while (bucket < px && bucket < 1024) bucket = bucket shl 1
        return bucket
    }

    private fun decodeFile(file: File, bucket: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if (bucket <= 128) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeFile(file: File, bitmap: Bitmap) {
        try {
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: Exception) {
            // The cache is optional; ignore write failures.
        }
    }

    private fun extract(uri: Uri, bucket: Int): Extracted {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val data = retriever.embeddedPicture ?: return Extracted.None
            decodeScaled(data, bucket)?.let { Extracted.Found(it) } ?: Extracted.None
        } catch (e: Exception) {
            // Could not read the file right now: this is NOT proof that there is no artwork.
            Extracted.Error
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing useful to do.
            }
        }
    }

    private fun decodeScaled(data: ByteArray, bucket: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val minSide = min(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (minSide / (sample * 2) >= bucket) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (bucket <= 128) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
        val shortSide = min(decoded.width, decoded.height)
        if (shortSide <= bucket) return decoded

        val scale = bucket.toFloat() / shortSide
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private companion object {
        /** Upper bound for the in-memory miss/failure bookkeeping. */
        const val MAX_TRACKED = 4000
    }
}
