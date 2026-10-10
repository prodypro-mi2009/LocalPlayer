package com.localplayer.music.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.playback.ArtworkRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Picks ONE accent colour from the current artwork (the "seed"); the screens derive their tonal
 * background / glow from it. Pipeline: cached small bitmap -> 16x16 sample -> most vivid hue bucket.
 * Results (also failures) are cached per artwork, so a colour is computed at most once per track.
 */
object ArtworkTone {
    private const val NONE = 0 // cached "no usable colour" (a real seed always has alpha 0xFF)
    private val cache = LruCache<String, Int>(128)

    private fun key(ref: ArtworkRef) = "${ref.trackId}:${ref.lastModified}"

    fun cached(ref: ArtworkRef): Int? = cache.get(key(ref))?.takeIf { it != NONE }

    suspend fun seedFor(
        ref: ArtworkRef,
        load: suspend (ArtworkRef) -> Bitmap?,
    ): Int? {
        val k = key(ref)
        cache.get(k)?.let { return it.takeIf { c -> c != NONE } }
        val seed = withContext(Dispatchers.Default) {
            try {
                val bitmap = load(ref) ?: return@withContext null
                val small = Bitmap.createScaledBitmap(bitmap, SAMPLE, SAMPLE, true)
                val pixels = IntArray(SAMPLE * SAMPLE)
                small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
                if (small !== bitmap) small.recycle()
                fromPixels(pixels)
            } catch (e: Exception) {
                null // extraction failure: callers fall back to the theme colour
            }
        }
        cache.put(k, seed ?: NONE)
        return seed
    }

    /**
     * Pure colour logic (JVM-testable). Pixels are ARGB. Pixels are grouped in 12 hue buckets weighted by
     * saturation * brightness (so vivid colours win over grey/near-black/near-white); the winning bucket's
     * average is returned. Null when the picture has no usable colour (greyscale, too dark, too bright).
     */
    fun fromPixels(pixels: IntArray): Int? {
        val weight = DoubleArray(BUCKETS)
        val r = DoubleArray(BUCKETS)
        val g = DoubleArray(BUCKETS)
        val b = DoubleArray(BUCKETS)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val red = (p shr 16) and 0xFF
            val green = (p shr 8) and 0xFF
            val blue = p and 0xFF
            val maxC = max(red, max(green, blue))
            val minC = min(red, min(green, blue))
            if (maxC == 0) continue
            val value = maxC / 255.0
            val sat = (maxC - minC).toDouble() / maxC
            if (sat < 0.18 || value < 0.18 || value > 0.97) continue
            val delta = (maxC - minC).toDouble()
            var hue = when (maxC) {
                red -> ((green - blue) / delta) % 6.0
                green -> (blue - red) / delta + 2.0
                else -> (red - green) / delta + 4.0
            } * 60.0
            if (hue < 0) hue += 360.0
            val bucket = (hue / 30.0).toInt().coerceIn(0, BUCKETS - 1)
            val w = sat * value
            weight[bucket] += w
            r[bucket] += red * w
            g[bucket] += green * w
            b[bucket] += blue * w
        }
        var best = -1
        for (i in 0 until BUCKETS) if (weight[i] > 0 && (best == -1 || weight[i] > weight[best])) best = i
        if (best == -1 || weight[best] < MIN_WEIGHT) return null
        val rr = (r[best] / weight[best]).toInt().coerceIn(0, 255)
        val gg = (g[best] / weight[best]).toInt().coerceIn(0, 255)
        val bb = (b[best] / weight[best]).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private const val SAMPLE = 16
    private const val BUCKETS = 12
    private const val MIN_WEIGHT = 1.0
}

/**
 * The accent colour of the artwork, or null while loading / when disabled / when none could be found.
 * Callers then use the theme colour, which is the safe fallback.
 */
@Composable
fun rememberArtworkTone(artwork: ArtworkRef?, enabled: Boolean): State<Color?> {
    val repository = (LocalContext.current.applicationContext as LocalPlayerApp).container.artwork
    return produceState<Color?>(initialValue = null, artwork, enabled) {
        value = if (!enabled || artwork == null) {
            null
        } else {
            ArtworkTone.cached(artwork)?.let { Color(it) }
                ?: ArtworkTone.seedFor(artwork) { ref ->
                    repository.load(ref.trackId, ref.sourceUri, ref.lastModified, 96)
                }?.let { Color(it) }
        }
    }
}
