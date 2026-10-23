package com.localplayer.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A touch this close to the thumb grabs it (keeps the finger -> thumb offset, so the thumb never jumps). */
private val GRAB_ZONE = 28.dp

/**
 * Wavy progress line: the played part is a wave, the rest a straight line, with a round thumb.
 * Tap or drag to seek; while the finger is down the thumb follows it and [onSeek] is called once on release
 * (no seek storm), [onScrub] while dragging.
 *
 * [fraction] (0..1) is read ONLY inside the draw scope, so a playback tick redraws this bar without any
 * recomposition. The accessibility tree uses the coarse [semanticFraction] so it is not rebuilt every step.
 */
@Composable
fun WaveSeekBar(
    fraction: () -> Float,
    semanticFraction: Float,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    description: String,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    /** True while playing: the wave drifts along the line; paused, it eases to a calmer ripple. */
    animate: Boolean = false,
) {
    // The pointer handlers live as long as the composable; they must always call the LATEST callbacks
    // (a stale lambda once made a seek use the first track's duration).
    val currentFraction = rememberUpdatedState(fraction)
    val currentScrub = rememberUpdatedState(onScrub)
    val currentSeek = rememberUpdatedState(onSeek)

    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var grabOffsetPx by remember { mutableFloatStateOf(0f) }

    val reduce = LocalReduceMotion.current
    val thumbScale = animateFloatAsState(
        targetValue = if (dragging && !reduce) 1.4f else 1f,
        animationSpec = Motion.PressSpring,
        label = "thumbScale",
    )

    // Alive: the wave travels while playing (one revolution every ~1.6 s) and calms down when paused.
    val live = animate && !reduce
    val amp = animateFloatAsState(
        targetValue = if (reduce) 1f else if (animate) 1f else 0.4f,
        animationSpec = Motion.PressSpring,
        label = "waveAmp",
    )
    val phaseState = if (live) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "wavePhase",
        )
    } else {
        null
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(semanticFraction.coerceIn(0f, 1f), 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentSeek.value((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                val grabZonePx = GRAB_ZONE.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val thumbX = currentFraction.value().coerceIn(0f, 1f) * widthPx
                        // Direct manipulation: on the thumb -> keep the offset (no jump); elsewhere -> jump to the finger.
                        grabOffsetPx = if (abs(offset.x - thumbX) <= grabZonePx) thumbX - offset.x else 0f
                        dragging = true
                        dragFraction = ((offset.x + grabOffsetPx) / widthPx).coerceIn(0f, 1f)
                        currentScrub.value(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = ((change.position.x + grabOffsetPx) / widthPx).coerceIn(0f, 1f)
                        currentScrub.value(dragFraction)
                    },
                    onDragEnd = {
                        dragging = false
                        currentSeek.value(dragFraction)
                    },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        val activeW = 6.dp.toPx()
        val trackW = 4.5.dp.toPx()
        val amplitude = 6.dp.toPx() * amp.value
        val wavelength = 30.dp.toPx()
        val phase = phaseState?.value ?: 0f
        val thumbR = 8.dp.toPx()
        val gap = 6.dp.toPx()
        val endDotR = 2.5.dp.toPx()
        val midY = size.height / 2f
        val shown = (if (dragging) dragFraction else fraction()).coerceIn(0f, 1f)
        val thumbX = (size.width * shown).coerceIn(thumbR, size.width - endDotR)

        // Remaining part: a thin straight line that starts a small gap after the thumb, ending in a round dot.
        val restStart = thumbX + thumbR * thumbScale.value + gap
        if (restStart < size.width - endDotR * 2f) {
            drawLine(
                color = trackColor,
                start = Offset(restStart, midY),
                end = Offset(size.width - endDotR, midY),
                strokeWidth = trackW,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = activeColor, radius = endDotR, center = Offset(size.width - endDotR, midY))
        // Played part: wave (flat at the very start so it grows out of the baseline).
        val path = Path()
        var x = 0f
        path.moveTo(0f, midY)
        while (x < thumbX) {
            val ramp = (x / wavelength).coerceAtMost(1f)
            val y = midY + amplitude * ramp * sin((x / wavelength) * 2f * PI.toFloat() - phase)
            path.lineTo(x, y)
            x += 2f
        }
        path.lineTo(thumbX, midY)
        drawPath(path, color = activeColor, style = Stroke(width = activeW, cap = StrokeCap.Round))
        drawCircle(color = activeColor, radius = thumbR * thumbScale.value, center = Offset(thumbX, midY))
    }
}
