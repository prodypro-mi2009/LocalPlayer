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
 * The colour of the current artwork as ONE tone ("the seed"); the screens derive their tonal background from it.
 * It is a MIX of all the colours of the picture, like paint poured into one bucket (not the single most vivid hue),
 * so a grey cover gives grey and a red-and-white cover gives a red-ish rose. Pipeline: cached small bitmap -> 16x16
 * sample -> [mix]. Results (also failures) are cached per artwork: computed at most once per track.
 * Nothing here runs unless the user chose "colours from the cover" (see rememberArtworkTone).
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
                mix(pixels)
            } catch (e: Exception) {
                null // extraction failure: callers fall back to the theme colour
            }
        }
        cache.put(k, seed ?: NONE)
        return seed
    }

    /**
     * Pure colour logic (JVM-testable). Pixels are ARGB. The result is the average of all opaque pixels, with two rules
     * that keep the text readable (the screens draw white text on top of this tone):
     *  1. If light ("white") pixels make up more than [whiteCap] of the picture, their weight is reduced until they make
     *     up exactly [whiteCap]; so the OTHER colours of the cover are emphasised, and only then (a cover that is 80 %
     *     white would otherwise give a near-white tone).
     *  2. The final lightness is limited to [MAX_LIGHTNESS] (hue and saturation are kept), so even an all-white cover
     *     gives a calm grey, never a bright background.
     * Null only when the picture has no opaque pixel.
     */
    fun mix(pixels: IntArray, whiteCap: Float = WHITE_CAP): Int? {
        var count = 0
        var light = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            count++
            if (lightness(p) >= LIGHT_LEVEL) light++
        }
        if (count == 0) return null
        val share = light.toDouble() / count
        val lightWeight =
            if (share > whiteCap && light < count) whiteCap * (count - light) / ((1.0 - whiteCap) * light) else 1.0
        var weight = 0.0
        var r = 0.0
        var g = 0.0
        var b = 0.0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val w = if (lightness(p) >= LIGHT_LEVEL) lightWeight else 1.0
            weight += w
            r += ((p shr 16) and 0xFF) * w
            g += ((p shr 8) and 0xFF) * w
            b += (p and 0xFF) * w
        }
        return limitLightness(
            (r / weight).toInt().coerceIn(0, 255),
            (g / weight).toInt().coerceIn(0, 255),
            (b / weight).toInt().coerceIn(0, 255),
        )
    }

    /** HSL lightness 0..1 of an ARGB pixel. */
    private fun lightness(p: Int): Double {
        val red = (p shr 16) and 0xFF
        val green = (p shr 8) and 0xFF
        val blue = p and 0xFF
        return (max(red, max(green, blue)) + min(red, min(green, blue))) / 510.0
    }

    /** Keeps hue and (HSL) saturation, lowers the lightness to [MAX_LIGHTNESS] when it is higher. */
    private fun limitLightness(r: Int, g: Int, b: Int): Int {
        val maxC = max(r, max(g, b)) / 255.0
        val minC = min(r, min(g, b)) / 255.0
        val l = (maxC + minC) / 2.0
        if (l <= MAX_LIGHTNESS) return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val d = maxC - minC
        val s = if (d == 0.0) 0.0 else d / (1.0 - kotlin.math.abs(2.0 * l - 1.0))
        val h = if (d == 0.0) 0.0 else when (maxC) {
            r / 255.0 -> (((g - b) / 255.0 / d) % 6.0 + 6.0) % 6.0
            g / 255.0 -> (b - r) / 255.0 / d + 2.0
            else -> (r - g) / 255.0 / d + 4.0
        }
        val l2 = MAX_LIGHTNESS
        val c = (1.0 - kotlin.math.abs(2.0 * l2 - 1.0)) * s
        val x = c * (1.0 - kotlin.math.abs(h % 2.0 - 1.0))
        val m = l2 - c / 2.0
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val rr = ((r1 + m) * 255).toInt().coerceIn(0, 255)
        val gg = ((g1 + m) * 255).toInt().coerceIn(0, 255)
        val bb = ((b1 + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    private const val SAMPLE = 16

    /** Share of "white" pixels above which the other colours start to be emphasised. */
    const val WHITE_CAP = 0.40f

    /** Pixels at or above this HSL lightness count as "white" (also pale pastels, which would wash out the text). */
    private const val LIGHT_LEVEL = 0.80

    /** Highest lightness of the final tone (white text stays readable on it). */
    const val MAX_LIGHTNESS = 0.40
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
