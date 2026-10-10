package com.localplayer.music.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Heavy, tight, italic display style for the expressive headings (Favorites, Genres). Uses the system
 * font at Black weight; no font files are bundled, so it works offline and costs nothing at startup.
 */
val DisplayHeavy = TextStyle(
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1).sp,
)

/** Smaller variant for cards. */
val DisplayHeavyCard = DisplayHeavy.copy(fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-0.5).sp)
