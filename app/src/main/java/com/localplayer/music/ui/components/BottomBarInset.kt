package com.localplayer.music.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the floating bottom bars (mini player + navigation + the system navigation area). The bars are drawn
 * ON TOP of the screens (so lists scroll visibly underneath them); every scrolling container adds this value as
 * bottom content padding, which lets the last item scroll above the bars and stay selectable.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Bottom content padding for a list: the floating bars plus [extra]. */
@Composable
fun barPadding(extra: Dp = 0.dp, horizontal: Dp = 0.dp, top: Dp = 0.dp): PaddingValues =
    PaddingValues(start = horizontal, top = top, end = horizontal, bottom = LocalBottomBarInset.current + extra)

/**
 * Bottom space for a control that must sit ABOVE the floating bars (a Save button, "Add selected"). While the
 * keyboard is open the bars are hidden behind it and the screen already lifts its content by the keyboard height,
 * so the keyboard height is subtracted: the control never floats higher than needed.
 */
@Composable
fun barInsetBelowKeyboard(): Dp {
    val density = LocalDensity.current
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    return (LocalBottomBarInset.current - keyboard).coerceAtLeast(0.dp)
}
