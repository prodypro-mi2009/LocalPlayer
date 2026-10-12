package com.localplayer.music.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.localplayer.music.R
import com.localplayer.music.data.personalization.ColorMath
import com.localplayer.music.data.personalization.ControlColorGroup
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.FontCategory
import com.localplayer.music.data.personalization.FontChoice
import com.localplayer.music.data.personalization.FontChoices

/** The user's colour per button group, as chosen in Settings (not yet checked against the background). */
val LocalControlColors = compositionLocalOf { ControlColors.None }

/**
 * The colour to draw a control of [group] with, or null when the user did not customise it (the caller then uses
 * its normal theme colour). The saved colour is only adjusted when it would be hard to see on [background] (for
 * example a dark blue picked in the dark theme and then used in the light one), so contrast always holds.
 */
@Composable
fun controlColor(group: ControlColorGroup, background: Color = MaterialTheme.colorScheme.surface): Color? {
    val saved = LocalControlColors.current[group] ?: return null
    return Color(ColorMath.ensureContrast(saved, background.toArgb(), group.minContrast))
}

/** Black or white, whichever is readable on [container] (used for icons drawn on a user-coloured button). */
fun onColorFor(container: Color): Color = Color(ColorMath.onColorFor(container.toArgb()))

/** The font families of the five text categories; null = the normal default font. */
class AppFonts(
    val songTitle: FontFamily? = null,
    val artist: FontFamily? = null,
    val albumGenre: FontFamily? = null,
    val heading: FontFamily? = null,
    val general: FontFamily? = null,
    val lyrics: FontFamily? = null,
) {
    companion object {
        val Default = AppFonts()

        fun resolve(choices: FontChoices, customFontFile: java.io.File?): AppFonts {
            fun family(category: FontCategory) = familyFor(choices[category], customFontFile)
            return AppFonts(
                songTitle = family(FontCategory.SONG_TITLE),
                artist = family(FontCategory.ARTIST),
                albumGenre = family(FontCategory.ALBUM_GENRE),
                heading = family(FontCategory.HEADING),
                general = family(FontCategory.GENERAL),
                lyrics = family(FontCategory.LYRICS),
            )
        }

        /**
         * Android system fonts are used by name (nothing bundled, they cover Greek and Latin). Doto (dot matrix,
         * SIL OFL) is bundled and covers Latin only: Greek text in it falls back to the default font letter by
         * letter. The imported font is whatever file the user chose.
         */
        fun familyFor(choice: FontChoice, customFontFile: java.io.File?): FontFamily? = when (choice) {
            FontChoice.DEFAULT -> null
            FontChoice.LIGHT -> FontFamily(Font(DeviceFontFamilyName("sans-serif-light")))
            FontChoice.MEDIUM -> FontFamily(Font(DeviceFontFamilyName("sans-serif-medium")))
            FontChoice.BLACK -> FontFamily(Font(DeviceFontFamilyName("sans-serif-black")))
            FontChoice.CONDENSED -> FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
            FontChoice.SERIF -> FontFamily.Serif
            FontChoice.MONOSPACE -> FontFamily.Monospace
            FontChoice.DOT_MATRIX -> FontFamily(Font(R.font.doto))
            FontChoice.CUSTOM -> customFontFile?.takeIf { it.isFile }?.let { FontFamily(Font(it)) }
        }
    }
}

val LocalAppFonts = compositionLocalOf { AppFonts.Default }

/** The same style with the user's font for this category (unchanged when the user did not choose one). */
@Composable
fun TextStyle.songTitleFont(): TextStyle = LocalAppFonts.current.songTitle?.let { copy(fontFamily = it) } ?: this

@Composable
fun TextStyle.artistFont(): TextStyle = LocalAppFonts.current.artist?.let { copy(fontFamily = it) } ?: this

@Composable
fun TextStyle.albumGenreFont(): TextStyle = LocalAppFonts.current.albumGenre?.let { copy(fontFamily = it) } ?: this

@Composable
fun TextStyle.headingFont(): TextStyle = LocalAppFonts.current.heading?.let { copy(fontFamily = it) } ?: this

@Composable
fun TextStyle.lyricsFont(): TextStyle = LocalAppFonts.current.lyrics?.let { copy(fontFamily = it) } ?: this

/**
 * Material typography with the "headings" font on display/headline/titleLarge and the "general text" font on
 * everything else (titleMedium/Small, body, label). Sizes and line heights are untouched, so layouts do not move.
 */
fun appTypography(fonts: AppFonts): Typography {
    val base = Typography()
    val heading = fonts.heading
    val general = fonts.general
    fun TextStyle.h() = if (heading != null) copy(fontFamily = heading) else this
    fun TextStyle.g() = if (general != null) copy(fontFamily = general) else this
    return base.copy(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h(),
        titleMedium = base.titleMedium.g(), titleSmall = base.titleSmall.g(),
        bodyLarge = base.bodyLarge.g(), bodyMedium = base.bodyMedium.g(), bodySmall = base.bodySmall.g(),
        labelLarge = base.labelLarge.g(), labelMedium = base.labelMedium.g(), labelSmall = base.labelSmall.g(),
    )
}
