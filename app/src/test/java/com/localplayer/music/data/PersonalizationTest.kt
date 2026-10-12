package com.localplayer.music.data

import com.localplayer.music.data.personalization.ColorMath
import com.localplayer.music.data.personalization.ControlColorGroup
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.FontCategory
import com.localplayer.music.data.personalization.FontChoice
import com.localplayer.music.data.personalization.FontChoices
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.data.personalization.PureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun hexParsingAcceptsCommonFormsAndRejectsGarbage() {
        assertEquals(0xFF1E88E5.toInt(), ColorMath.parseHex("#1E88E5"))
        assertEquals(0xFF1E88E5.toInt(), ColorMath.parseHex("1e88e5"))
        assertEquals(0xFFFFAA00.toInt(), ColorMath.parseHex("#fa0"))
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex("#1234567"))
        assertEquals("#1E88E5", ColorMath.toHex(0xFF1E88E5.toInt()))
    }

    @Test fun contrastMatchesTheWcagReferenceValues() {
        assertEquals(21.0, ColorMath.contrast(black, white), 0.01)
        assertEquals(1.0, ColorMath.contrast(white, white), 0.001)
        // #777777 on white is the classic 4.48:1 example.
        assertEquals(4.48, ColorMath.contrast(0xFF777777.toInt(), white), 0.02)
    }

    @Test fun onColorIsAlwaysReadable() {
        for (c in listOf(0xFF0000FF, 0xFFFFFF00, 0xFF808080, 0xFF7F00FF, 0xFF00FF00, 0xFFFF0000)) {
            val bg = c.toInt()
            assertTrue(ColorMath.contrast(bg, ColorMath.onColorFor(bg)) >= 4.5)
        }
    }

    @Test fun ensureContrastKeepsGoodColoursAndFixesBadOnes() {
        val blue = 0xFF1E88E5.toInt()
        assertEquals(blue, ColorMath.ensureContrast(blue, black, 3.0))
        val darkBlue = 0xFF000080.toInt()
        val fixed = ColorMath.ensureContrast(darkBlue, black, 3.0)
        assertTrue(fixed != darkBlue)
        assertTrue(ColorMath.contrast(fixed, black) >= 3.0)
        val paleYellow = 0xFFFFFF99.toInt()
        assertTrue(ColorMath.contrast(ColorMath.ensureContrast(paleYellow, white, 4.5), white) >= 4.5)
        // White text colour on a white background is brought to a readable dark colour.
        assertTrue(ColorMath.contrast(ColorMath.ensureContrast(white, white, 4.5), white) >= 4.5)
    }

    @Test fun hsvRoundTrips() {
        for (c in listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF1E88E5, 0xFF7B1FA2, 0xFF808080, 0xFFFFFFFF, 0xFF000000)) {
            val argb = c.toInt()
            val hsv = ColorMath.argbToHsv(argb)
            assertEquals(argb, ColorMath.hsvToArgb(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test fun controlColorsRoundTripAndRepair() {
        val colors = ControlColors.None
            .with(ControlColorGroup.PLAY_PAUSE, 0xFF1E88E5.toInt())
            .with(ControlColorGroup.PLAYBACK_OPTIONS, 0xFFE53935.toInt())
        val raw = colors.serialize()
        val back = ControlColors.parse(raw)
        assertEquals(0xFF1E88E5.toInt(), back[ControlColorGroup.PLAY_PAUSE])
        assertEquals(0xFFE53935.toInt(), back[ControlColorGroup.PLAYBACK_OPTIONS])
        assertNull(back[ControlColorGroup.PREV_NEXT])
        // Removing the last colour gives "nothing saved".
        assertNull(back.with(ControlColorGroup.PLAY_PAUSE, null).with(ControlColorGroup.PLAYBACK_OPTIONS, null).serialize())
        // Garbage never throws and never invents colours.
        assertTrue(ControlColors.parse("nonsense;play_pause=#ZZZ;unknown=#FFFFFF;=").isEmpty)
        assertTrue(ControlColors.parse(null).isEmpty)
    }

    @Test fun thereIsNoGroupForTheFavoriteHeart() {
        assertTrue(ControlColorGroup.entries.none { it.key.contains("favorite") || it.key.contains("heart") })
    }

    @Test fun fontChoicesRoundTripAndDefaultIsNotStored() {
        val choices = FontChoices.Default
            .with(FontCategory.SONG_TITLE, FontChoice.DOT_MATRIX)
            .with(FontCategory.HEADING, FontChoice.SERIF)
        val back = FontChoices.parse(choices.serialize())
        assertEquals(FontChoice.DOT_MATRIX, back[FontCategory.SONG_TITLE])
        assertEquals(FontChoice.SERIF, back[FontCategory.HEADING])
        assertEquals(FontChoice.DEFAULT, back[FontCategory.ARTIST])
        assertNull(back.with(FontCategory.SONG_TITLE, FontChoice.DEFAULT).with(FontCategory.HEADING, FontChoice.DEFAULT).serialize())
        assertTrue(FontChoices.parse("song_title=nope;zzz=serif;artist").isEmpty)
    }
}

class PureModeTest {
    @Test fun blackAndWhiteAreExactOpposites() {
        val black = com.localplayer.music.data.personalization.PurePalette.of(com.localplayer.music.data.personalization.PureMode.BLACK)!!
        assertEquals(0xFF000000.toInt(), black.surface)
        assertEquals(0xFFFFFFFF.toInt(), black.control)
        assertEquals(black.surface, black.onControl)
        assertEquals(black.control, black.onSurface)
        val white = com.localplayer.music.data.personalization.PurePalette.of(com.localplayer.music.data.personalization.PureMode.WHITE)!!
        assertEquals(0xFFFFFFFF.toInt(), white.surface)
        assertEquals(0xFF000000.toInt(), white.control)
        assertNull(com.localplayer.music.data.personalization.PurePalette.of(com.localplayer.music.data.personalization.PureMode.OFF))
    }

    @Test fun everyPureColourIsGreyAndReadable() {
        for (mode in listOf(com.localplayer.music.data.personalization.PureMode.BLACK, com.localplayer.music.data.personalization.PureMode.WHITE)) {
            val p = com.localplayer.music.data.personalization.PurePalette.of(mode)!!
            for (c in listOf(p.surface, p.control, p.secondaryText, p.outline, p.outlineVariant)) {
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                assertTrue("not grey: ${ColorMath.toHex(c)}", r == g && g == b)
            }
            assertEquals(21.0, ColorMath.contrast(p.surface, p.control), 0.01)
            assertTrue(ColorMath.contrast(p.surface, p.secondaryText) >= 7.0)
            // Track / secondary-container grey must differ from the control colour but keep control text readable on it.
            assertTrue(ColorMath.contrast(p.outlineVariant, p.control) >= 4.5)
            assertTrue(ColorMath.contrast(p.outlineVariant, p.surface) >= 1.5)
        }
    }

    @Test fun pureModeForcesTheThemeAndParsesSafely() {
        val m = com.localplayer.music.data.personalization.PureMode.Companion
        assertEquals(true, PureMode.BLACK.forcedDark)
        assertEquals(false, PureMode.WHITE.forcedDark)
        assertNull(PureMode.OFF.forcedDark)
        assertEquals(PureMode.OFF, m.fromKey(null))
        assertEquals(PureMode.OFF, m.fromKey("garbage"))
        assertEquals(PureMode.WHITE, m.fromKey("white"))
    }

    @Test fun lyricsAlignmentParsesSafelyAndLyricsFontPersists() {
        val a = com.localplayer.music.data.personalization.LyricsAlignment.Companion
        assertEquals(LyricsAlignment.START, a.fromKey(null))
        assertEquals(LyricsAlignment.CENTER, a.fromKey("center"))
        assertEquals(LyricsAlignment.END, a.fromKey("end"))
        assertEquals(LyricsAlignment.START, a.fromKey("x"))
        val back = FontChoices.parse(FontChoices.Default.with(FontCategory.LYRICS, FontChoice.SERIF).serialize())
        assertEquals(FontChoice.SERIF, back[FontCategory.LYRICS])
    }
}
