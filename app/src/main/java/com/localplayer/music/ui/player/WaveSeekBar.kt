package com.localplayer.music.ui.player

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Wavy progress line: the played part is a wave, the rest a straight line, with a round thumb.
 * Tap or drag to seek. [fraction] is the real playback position (0..1); while the finger is down the
 * thumb follows it and [onSeek] is called once on release (no seek storm).
 */
@Composable
fun WaveSeekBar(
    fraction: Float,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    description: String,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragFraction else fraction).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragFraction)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragFraction)
                    },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        widthPx = size.width.coerceAtLeast(1f)
        val strokeW = 4.dp.toPx()
        val amplitude = 5.dp.toPx()
        val wavelength = 28.dp.toPx()
        val midY = size.height / 2f
        val thumbX = (size.width * shown).coerceIn(strokeW, size.width - strokeW)

        // Remaining part: straight line.
        drawLine(
            color = trackColor,
            start = Offset(thumbX, midY),
            end = Offset(size.width, midY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        // Played part: wave (flat at the very start so it grows out of the baseline).
        val path = Path()
        var x = 0f
        path.moveTo(0f, midY)
        while (x < thumbX) {
            val ramp = (x / (wavelength)).coerceAtMost(1f)
            val y = midY + amplitude * ramp * sin((x / wavelength) * 2f * PI.toFloat())
            path.lineTo(x, y)
            x += 2f
        }
        path.lineTo(thumbX, midY)
        drawPath(path, color = activeColor, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        drawCircle(color = activeColor, radius = 8.dp.toPx(), center = Offset(thumbX, midY))
    }
}
