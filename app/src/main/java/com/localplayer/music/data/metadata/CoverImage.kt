package com.localplayer.music.data.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Turns a picture chosen by the user into the JPEG bytes that are embedded as the cover. */
object CoverImage {
    const val MAX_EDGE = 1200
    private const val JPEG_QUALITY = 90

    class Prepared(val bytes: ByteArray, val width: Int, val height: Int) {
        val mimeType: String get() = "image/jpeg"
    }

    /**
     * Decodes [uri] (without loading a huge bitmap at full size), scales it down to at most [MAX_EDGE] pixels on
     * the long edge and re-encodes it as JPEG. Transparent pictures are put on white. Null when it is not a readable picture.
     */
    fun prepare(context: Context, uri: Uri): Prepared? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds decodeStream always returns null; only the options are filled in.
            val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
            if (!opened) return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

            val scale = min(1f, MAX_EDGE / max(decoded.width, decoded.height).toFloat())
            val width = max(1, (decoded.width * scale).roundToInt())
            val height = max(1, (decoded.height * scale).roundToInt())
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(decoded, width, height, true) else decoded

            val flat = if (scaled.hasAlpha()) {
                Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(scaled, 0f, 0f, null)
                    }
                }
            } else {
                scaled
            }
            val out = ByteArrayOutputStream()
            if (!flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return null
            Prepared(out.toByteArray(), flat.width, flat.height)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
