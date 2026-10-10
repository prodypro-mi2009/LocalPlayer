package com.localplayer.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Vertical brush of the artwork-derived background. EVERY stop has the same hue (only the alpha changes along the
 * curve in [ArtworkGradient]); fading to `Color.Transparent` instead would pass through darkened colours, because
 * Transparent is black with alpha 0.
 *
 * @param strength multiplies every alpha (1 = Now Playing; the window backdrop behind the library uses less).
 * @param endY end of the gradient in pixels; the default is the full height of the area it is drawn on.
 */
fun artworkVerticalBrush(tone: Color, strength: Float = 1f, endY: Float = Float.POSITIVE_INFINITY): Brush {
    val top = ArtworkGradient.topAlphaFor(tone.luminance()) * strength
    val stops = ArtworkGradient.stops(topAlpha = top)
    return Brush.verticalGradient(
        colorStops = stops.map { (fraction, alpha) -> fraction to tone.copy(alpha = alpha) }.toTypedArray(),
        endY = endY,
    )
}

/** The lyrics page: the same tone, but tinted over the whole height (no black block at the bottom). */
fun lyricsVerticalBrush(tone: Color): Brush {
    val scale = ArtworkGradient.topAlphaFor(tone.luminance()) / ArtworkGradient.TOP_ALPHA
    val stops = ArtworkGradient.lyricsStops()
    return Brush.verticalGradient(
        colorStops = stops.map { (fraction, alpha) -> fraction to tone.copy(alpha = alpha * scale) }.toTypedArray(),
    )
}
