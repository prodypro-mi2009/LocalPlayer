package com.localplayer.music.data.personalization

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure helpers for the user's own colours and fonts: parsing, saving, contrast. No Android in here, so every rule
 * has a plain JVM unit test. Colours are ARGB ints that are always fully opaque (alpha 0xFF).
 */
object ColorMath {
    const val MIN_CONTRAST_ICON = 3.0
    const val MIN_CONTRAST_TEXT = 4.5

    /** "#RRGGBB", "RRGGBB", "#RGB" or "RGB" (any case) -> opaque ARGB, or null when it is not a valid colour. */
    fun parseHex(text: String): Int? {
        var s = text.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length != 6 || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF shl 24) or s.toInt(16)
    }

    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    private fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF

    private fun lin(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, 0 (black) .. 1 (white). */
    fun luminance(argb: Int): Double =
        0.2126 * lin(channel(argb, 16)) + 0.7152 * lin(channel(argb, 8)) + 0.0722 * lin(channel(argb, 0))

    /** WCAG contrast ratio, 1 .. 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Black or white, whichever reads better on [background] (always at least 4.5:1). */
    fun onColorFor(background: Int): Int {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        return if (contrast(background, black) >= contrast(background, white)) black else white
    }

    private fun mix(from: Int, to: Int, t: Double): Int {
        fun c(shift: Int) = (channel(from, shift) + (channel(to, shift) - channel(from, shift)) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    /**
     * [color] itself when it already has at least [min] contrast against [against]; otherwise the closest colour of
     * the same hue that does (lightened on a dark background, darkened on a light one).
     */
    fun ensureContrast(color: Int, against: Int, min: Double): Int {
        if (contrast(color, against) >= min) return color
        val target = if (luminance(against) < 0.5) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        var step = 1
        while (step <= 20) {
            val candidate = mix(color, target, step / 20.0)
            if (contrast(candidate, against) >= min) return candidate
            step++
        }
        return target
    }

    /** h 0..360, s 0..1, v 0..1 -> opaque ARGB. */
    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        fun c(x: Float) = (x * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** Opaque ARGB -> [h 0..360, s 0..1, v 0..1]. */
    fun argbToHsv(argb: Int): FloatArray {
        val r = channel(argb, 16) / 255f
        val g = channel(argb, 8) / 255f
        val b = channel(argb, 0) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf(if (h < 0f) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
    }
}

/** Groups of buttons/symbols the user can colour separately. The Favorite heart is deliberately NOT one of them. */
enum class ControlColorGroup(val key: String, val minContrast: Double) {
    PLAY_PAUSE("play_pause", ColorMath.MIN_CONTRAST_ICON),
    PREV_NEXT("prev_next", ColorMath.MIN_CONTRAST_ICON),
    PLAYBACK_OPTIONS("playback_options", ColorMath.MIN_CONTRAST_ICON),
    NOW_PLAYING_TEXT("now_playing_text", ColorMath.MIN_CONTRAST_TEXT),
    NAVIGATION("navigation", ColorMath.MIN_CONTRAST_ICON),
}

/** The user's colour per group; a group that is absent uses the normal theme colours. */
class ControlColors private constructor(private val map: Map<ControlColorGroup, Int>) {
    operator fun get(group: ControlColorGroup): Int? = map[group]
    val isEmpty: Boolean get() = map.isEmpty()

    fun with(group: ControlColorGroup, argb: Int?): ControlColors =
        ControlColors(if (argb == null) map - group else map + (group to ((argb and 0xFFFFFF) or (0xFF shl 24))))

    /** "key=#RRGGBB;key=#RRGGBB", or null when nothing is customised. */
    fun serialize(): String? =
        if (map.isEmpty()) null else ControlColorGroup.entries.filter { it in map }
            .joinToString(";") { "${it.key}=${ColorMath.toHex(map.getValue(it))}" }

    companion object {
        val None = ControlColors(emptyMap())

        /** Never throws: unknown groups and malformed values are ignored. */
        fun parse(raw: String?): ControlColors {
            if (raw.isNullOrBlank()) return None
            val result = LinkedHashMap<ControlColorGroup, Int>()
            for (part in raw.split(';')) {
                val pieces = part.split('=')
                if (pieces.size != 2) continue
                val group = ControlColorGroup.entries.firstOrNull { it.key == pieces[0].trim() } ?: continue
                val color = ColorMath.parseHex(pieces[1]) ?: continue
                result[group] = color
            }
            return ControlColors(result)
        }
    }
}

/** Text categories that can have their own font. */
enum class FontCategory(val key: String) {
    SONG_TITLE("song_title"),
    ARTIST("artist"),
    ALBUM_GENRE("album_genre"),
    HEADING("heading"),
    GENERAL("general"),
    LYRICS("lyrics"),
}

/**
 * Font choices. Everything except [DOT_MATRIX] and [CUSTOM] is a font that ships with Android itself (nothing is
 * bundled for them). [DOT_MATRIX] is Doto, bundled under the SIL Open Font License (Latin letters only).
 * [CUSTOM] is a font file the user imported (for example NDot, which cannot be redistributed inside this app).
 */
enum class FontChoice(val key: String) {
    DEFAULT("default"),
    LIGHT("light"),
    MEDIUM("medium"),
    BLACK("black"),
    CONDENSED("condensed"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    DOT_MATRIX("dot_matrix"),
    CUSTOM("custom"),
}

class FontChoices private constructor(private val map: Map<FontCategory, FontChoice>) {
    operator fun get(category: FontCategory): FontChoice = map[category] ?: FontChoice.DEFAULT
    val isEmpty: Boolean get() = map.isEmpty()

    fun with(category: FontCategory, choice: FontChoice): FontChoices =
        FontChoices(if (choice == FontChoice.DEFAULT) map - category else map + (category to choice))

    fun serialize(): String? =
        if (map.isEmpty()) null else FontCategory.entries.filter { it in map }
            .joinToString(";") { "${it.key}=${map.getValue(it).key}" }

    companion object {
        val Default = FontChoices(emptyMap())

        fun parse(raw: String?): FontChoices {
            if (raw.isNullOrBlank()) return Default
            val result = LinkedHashMap<FontCategory, FontChoice>()
            for (part in raw.split(';')) {
                val pieces = part.split('=')
                if (pieces.size != 2) continue
                val category = FontCategory.entries.firstOrNull { it.key == pieces[0].trim() } ?: continue
                val choice = FontChoice.entries.firstOrNull { it.key == pieces[1].trim() } ?: continue
                if (choice != FontChoice.DEFAULT) result[category] = choice
            }
            return FontChoices(result)
        }
    }
}


/**
 * "Pure" appearance: no colour at all. OFF = normal theme. BLACK = one uniform #000000 everywhere with white
 * controls; WHITE = one uniform #FFFFFF everywhere with black controls. Overrides dynamic colour, artwork colours,
 * the background picture and the user's button colours (they would break the "exact opposite" rule).
 */
enum class PureMode(val key: String) {
    OFF("off"), BLACK("black"), WHITE("white");

    val isOn: Boolean get() = this != OFF

    /** True/false when the mode forces the dark/light theme, null when the normal theme mode decides. */
    val forcedDark: Boolean? get() = when (this) { OFF -> null; BLACK -> true; WHITE -> false }

    companion object {
        fun fromKey(key: String?): PureMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** The neutral (grey-only) colours of a pure mode, as opaque ARGB. [surface] is the one and only background. */
class PurePalette(val surface: Int, val control: Int) {
    /** Text/icons on [surface] and the control colour are exact opposites. */
    val onSurface: Int get() = control
    val onControl: Int get() = surface
    /** Secondary text: neutral grey that still reads clearly (>= 7:1) on [surface]. */
    val secondaryText: Int = if (surface == BLACK) 0xFFB3B3B3.toInt() else 0xFF4D4D4D.toInt()
    val outline: Int = if (surface == BLACK) 0xFF8C8C8C.toInt() else 0xFF737373.toInt()
    val outlineVariant: Int = if (surface == BLACK) 0xFF4A4A4A.toInt() else 0xFFB8B8B8.toInt()

    companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        fun of(mode: PureMode): PurePalette? = when (mode) {
            PureMode.OFF -> null
            PureMode.BLACK -> PurePalette(BLACK, WHITE)
            PureMode.WHITE -> PurePalette(WHITE, BLACK)
        }
    }
}

/** Alignment of the lyrics text. START/END follow the reading direction. */
enum class LyricsAlignment(val key: String) {
    START("start"), CENTER("center"), END("end");

    companion object {
        fun fromKey(key: String?): LyricsAlignment = entries.firstOrNull { it.key == key } ?: START
    }
}
