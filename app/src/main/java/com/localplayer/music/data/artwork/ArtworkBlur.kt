package com.localplayer.music.data.artwork

import android.graphics.Bitmap

/**
 * Cheap, safe background blur (lyrics texture and the "Blurred cover" background): the artwork is first shrunk to a tiny
 * bitmap and blurred there (a few thousand pixels, once), then simply scaled up when drawn. The blur is light (about 4 %
 * of the picture width by default; the user can choose less or more, see [MIN_LEVEL]..[MAX_LEVEL]) so the shapes and colours of the cover are still recognisable, and the colours get a small
 * saturation boost to make up for the veil that is drawn over the picture. No RenderEffect, no per-frame work,
 * no full-resolution bitmap. Any failure returns null and the caller shows a plain dark background.
 */
object ArtworkBlur {
    const val SIZE = 128
    const val PASSES = 2

    /** Blur strength chosen by the user: 1 = the cover stays easy to recognise, 5 = soft. Level 3 is the look of 1.9. */
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 5
    const val DEFAULT_LEVEL = 2

    /** The faint texture on the lyrics page keeps its old, fixed softness. */
    const val TEXTURE_LEVEL = 3

    /** Blur radius in pixels of the [SIZE] x [SIZE] bitmap for a level (level 3 = 6 px = about 4.7 % of the width). */
    fun radiusFor(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL) * 2
    const val SATURATION = 1.15f

    /** The few most recent blurred backgrounds (each is only 40x40 pixels). */
    private val cache = android.util.LruCache<String, Bitmap>(6)

    fun cached(key: String): Bitmap? = cache.get(key)

    // The last whole-app background, kept in one tiny file so it can be shown at once after a cold start.
    @Volatile private var lastSavedKey: String? = null

    private fun lastFile(context: android.content.Context) = java.io.File(context.filesDir, "blur_last.png")

    /** Reads the saved picture (a small picture, a few KB); null when there is none. Only called when the blur is on. */
    fun loadLast(context: android.content.Context): Bitmap? = try {
        lastFile(context).takeIf { it.isFile }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
    } catch (e: Exception) {
        null
    }

    /** Forgets the saved picture: nothing is playing, so the next start must begin with the plain background. */
    fun clearLast(context: android.content.Context) {
        lastSavedKey = null
        try {
            lastFile(context).delete()
        } catch (e: Exception) {
            // Only a convenience; ignore.
        }
    }

    /** Saves [bitmap] as the last picture (once per song; written to a temp file, then renamed). Call off the main thread. */
    fun saveLast(context: android.content.Context, key: String, bitmap: Bitmap) {
        if (key == lastSavedKey) return
        try {
            val target = lastFile(context)
            val tmp = java.io.File(target.path + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (tmp.renameTo(target)) lastSavedKey = key else tmp.delete()
        } catch (e: Exception) {
            // Only a convenience for the next start; ignore.
        }
    }
    fun remember(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun blurredSmall(source: Bitmap, level: Int = TEXTURE_LEVEL): Bitmap? = try {
        val small = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small !== source) small.recycle()
        val blurred = Saturation.boost(BoxBlur.blur(pixels, SIZE, SIZE, radius = radiusFor(level), passes = PASSES), SATURATION)
        Bitmap.createBitmap(blurred, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** Plain-JVM box blur over ARGB int pixels (kept free of Android classes so it is unit-tested). */
object BoxBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
        var current = pixels.copyOf()
        repeat(passes.coerceAtLeast(1)) {
            current = horizontal(current, width, height, radius)
            current = vertical(current, width, height, radius)
        }
        return current
    }

    private fun horizontal(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    val c = src[y * w + xx]
                    a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                out[y * w + x] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    private fun vertical(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val out = IntArray(src.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    val c = src[yy * w + x]
                    a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                out[y * w + x] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }
}

/** Plain-JVM saturation change over ARGB int pixels: every channel moves away from the pixel's grey by [factor]. */
object Saturation {
    fun boost(pixels: IntArray, factor: Float): IntArray {
        if (factor == 1f) return pixels
        return IntArray(pixels.size) { i ->
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val grey = (r + g + b) / 3f
            val nr = (grey + (r - grey) * factor).toInt().coerceIn(0, 255)
            val ng = (grey + (g - grey) * factor).toInt().coerceIn(0, 255)
            val nb = (grey + (b - grey) * factor).toInt().coerceIn(0, 255)
            (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
    }
}
