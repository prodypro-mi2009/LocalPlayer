package com.localplayer.music.data.artwork

import android.graphics.Bitmap

/**
 * Cheap, safe background blur for the lyrics view: the artwork is first shrunk to a tiny bitmap and blurred
 * there (a few thousand pixels, once), then simply scaled up when drawn. No RenderEffect, no per-frame work,
 * no full-resolution bitmap. Any failure returns null and the caller shows a plain dark background.
 */
object ArtworkBlur {
    const val SIZE = 40

    /** The few most recent blurred backgrounds (each is only 40x40 pixels). */
    private val cache = android.util.LruCache<String, Bitmap>(6)

    fun cached(key: String): Bitmap? = cache.get(key)
    fun remember(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun blurredSmall(source: Bitmap): Bitmap? = try {
        val small = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (small !== source) small.recycle()
        val blurred = BoxBlur.blur(pixels, SIZE, SIZE, radius = 3, passes = 3)
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
