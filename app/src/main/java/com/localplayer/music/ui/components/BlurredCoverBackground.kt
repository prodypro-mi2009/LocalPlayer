package com.localplayer.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** Opacity of the theme-coloured veil over the blurred cover: Now Playing / the whole app (more, so lists stay readable). */
const val NOW_PLAYING_BLUR_VEIL = 0.32f
const val APP_BLUR_VEIL = 0.55f

/**
 * The blurred cover as a full-screen background (the user's choice "Blurred cover"). The picture is the tiny pre-blurred
 * bitmap of [rememberBlurredArtwork] (made once per song), only scaled up here. The veil uses the theme's background colour,
 * so the text keeps its contrast in the dark and in the light theme. [image] null = still loading: just the veil.
 */
@Composable
fun BlurredCoverBackground(image: ImageBitmap?, veil: Float, modifier: Modifier = Modifier, veilColor: Color? = null) {
    // Two layers: the old picture stays fully opaque underneath while the new one fades in on top. (A plain Crossfade
    // fades the old one OUT as well, so for a moment neither is opaque and the dark window shows through = a flash.)
    var base by remember { mutableStateOf(image) }
    var top by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(image) {
        val shownNow = top ?: base
        if (image === shownNow) return@LaunchedEffect
        if (image == null) {
            base = null
            top = null
            return@LaunchedEffect
        }
        // A fade that was still running is accepted as it is, then the next picture fades over it.
        if (top != null) base = top
        top = image
        progress.snapTo(0f)
        progress.animateTo(1f, tween(450))
        base = image
        top = null
    }
    Box(modifier.fillMaxSize()) {
        base?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        top?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = progress.value },
            )
        }
        Box(Modifier.fillMaxSize().background((veilColor ?: MaterialTheme.colorScheme.background).copy(alpha = veil)))
    }
}

/**
 * The same picture as [BlurredCoverBackground], drawn straight into a draw scope (for the card of a screen that is being
 * left with a back step: it paints its own opaque background, which must look like the page did a moment ago).
 */
fun DrawScope.drawBlurredCover(image: ImageBitmap?, veil: Color) {
    if (image != null && image.width > 0 && image.height > 0) {
        val scale = max(size.width / image.width, size.height / image.height)
        val w = (image.width * scale).roundToInt()
        val h = (image.height * scale).roundToInt()
        clipRect {
            drawImage(
                image,
                dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                dstSize = IntSize(w, h),
            )
        }
    }
    drawRect(veil, size = Size(size.width, size.height))
}
