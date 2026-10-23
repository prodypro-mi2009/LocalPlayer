package com.localplayer.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.localplayer.music.data.ThemeMode
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.PureMode
import com.localplayer.music.data.personalization.PurePalette
import androidx.compose.runtime.compositionLocalOf

/** All fixed colors of the app live here. */
object AppColors {
    /** Main background of the dark theme: true black (AMOLED). */
    val TrueBlack = Color(0xFF000000)

    /** Main background of the light theme: true white. */
    val TrueWhite = Color(0xFFFFFFFF)

    /**
     * The Favorite heart. A FIXED colour on purpose: no dynamic colour, artwork tone or user colour setting may
     * change it (it is also the one colour the "custom colours" feature must never override).
     */
    val FavoriteRed = Color(0xFFFF1744)

    /** Seed-based fallback palette used on Android 8-11 or when dynamic color is off. */
    val SeedPrimaryDark = Color(0xFF6FDBA0)
    val SeedPrimaryLight = Color(0xFF006D40)
}

private val fallbackDark = darkColorScheme(
    primary = AppColors.SeedPrimaryDark,
    onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = Color(0xFF8CF8BB),
    secondary = Color(0xFFB3CCBB),
    tertiary = Color(0xFFA3CDDD),
    // Controlled neutral steps above true black, so rows, cards and the navigation pill stay visible.
    surfaceContainerLowest = AppColors.TrueBlack,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

private val fallbackLight = lightColorScheme(
    primary = AppColors.SeedPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8BB),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4E6354),
    tertiary = Color(0xFF3A6473),
    surfaceContainerLowest = AppColors.TrueWhite,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
)

/**
 * Material You dynamic color on Android 12+ (if enabled), a stable seed palette otherwise.
 * Whatever the palette, the MAIN BACKGROUND is always exactly #000000 (dark) or #FFFFFF (light);
 * the subtle surface containers keep their tonal values so rows, cards and sheets stay distinguishable.
 */
@Composable
fun LocalPlayerTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    controlColors: com.localplayer.music.data.personalization.ControlColors = com.localplayer.music.data.personalization.ControlColors.None,
    fonts: AppFonts = AppFonts.Default,
    pureMode: PureMode = PureMode.OFF,
    content: @Composable () -> Unit,
) {
    val dark = pureMode.forcedDark ?: themeMode.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> fallbackDark
        else -> fallbackLight
    }
    val pure = if (dark) AppColors.TrueBlack else AppColors.TrueWhite
    val palette = PurePalette.of(pureMode)
    val scheme = if (palette != null) pureScheme(palette) else base.copy(background = pure, surface = pure)
    androidx.compose.runtime.CompositionLocalProvider(
        // Pure mode: buttons are exactly the opposite of the background, so the user's own colours are not applied.
        LocalControlColors provides (if (palette != null) ControlColors.None else controlColors),
        LocalPureMode provides pureMode,
        LocalAppFonts provides fonts,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(fonts), content = content)
    }
}

/** The current pure mode, for the few places that draw their own backdrops (artwork tint, lyrics page). */
val LocalPureMode = compositionLocalOf { PureMode.OFF }

/**
 * Pure black / pure white: every surface role is the ONE background colour, every control (primary, tonal and
 * container colours, text, icons) is exactly the opposite colour, and the rest is neutral grey. No hue anywhere.
 * Only the error colour keeps its red (a warning must stay recognisable); the Favorite heart is drawn separately.
 */
private fun pureScheme(p: PurePalette): ColorScheme {
    val bg = Color(p.surface)
    val fg = Color(p.control)
    val grey = Color(p.secondaryText)
    val dark = p.surface == PurePalette.BLACK
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = fg, onPrimary = bg,
        primaryContainer = fg, onPrimaryContainer = bg,
        inversePrimary = bg,
        secondary = fg, onSecondary = bg,
        // Neutral grey (not the control colour): slider/progress tracks and swipe backgrounds must differ from the controls.
        secondaryContainer = Color(p.outlineVariant), onSecondaryContainer = fg,
        tertiary = fg, onTertiary = bg,
        tertiaryContainer = fg, onTertiaryContainer = bg,
        background = bg, onBackground = fg,
        surface = bg, onSurface = fg,
        surfaceVariant = bg, onSurfaceVariant = grey,
        surfaceTint = bg,
        inverseSurface = fg, inverseOnSurface = bg,
        outline = Color(p.outline), outlineVariant = Color(p.outlineVariant),
        scrim = Color.Black,
        surfaceBright = bg, surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = bg, surfaceContainer = bg,
        surfaceContainerHigh = bg, surfaceContainerHighest = bg,
    )
}

/** Tint behind the player/lyrics/backdrop when there is no artwork tone: the theme colour, or plain background in pure mode. */
@Composable
fun fallbackTint(): Color =
    if (LocalPureMode.current.isOn) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primaryContainer

/** Background of the row of the song that is playing now (a neutral grey in pure mode so the text stays readable). */
@Composable
fun currentRowColor(alpha: Float): Color =
    if (LocalPureMode.current.isOn) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)

/** Filled tonal buttons: exactly the opposite of the background in pure mode, the normal tonal colours otherwise. */
@Composable
fun tonalButtonColors(): androidx.compose.material3.ButtonColors =
    if (LocalPureMode.current.isOn) {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
    }
