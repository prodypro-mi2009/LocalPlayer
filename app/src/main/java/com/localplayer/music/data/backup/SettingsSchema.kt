package com.localplayer.music.data.backup

import com.localplayer.music.data.effects.EqModel
import com.localplayer.music.data.effects.EqPreset
import com.localplayer.music.data.effects.ReplayGainMode
import com.localplayer.music.data.mix.MixSettings
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.FontChoices
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.data.personalization.PureMode

enum class SettingType { BOOL, STRING, FLOAT }

/**
 * One backed-up setting. [activation] marks the master switches of OPTIONAL features (widgets, ReplayGain,
 * equalizer): an import never changes them unless the user explicitly opts in.
 */
class SettingDef(
    val key: String,
    val type: SettingType,
    val group: String,
    val activation: Boolean = false,
    val validate: (Any) -> Boolean,
)

/**
 * THE inventory of backed-up settings (derived from AppSettings, the only settings store of the app) and of the
 * settings that are deliberately NOT backed up. Pure Kotlin: validated on the JVM.
 */
object SettingsSchema {
    private fun bool(key: String, group: String, activation: Boolean = false) =
        SettingDef(key, SettingType.BOOL, group, activation) { it is Boolean }

    private fun text(key: String, group: String, maxLen: Int = 400, ok: (String) -> Boolean) =
        SettingDef(key, SettingType.STRING, group) { it is String && it.length <= maxLen && ok(it) }

    val all: List<SettingDef> = listOf(
        bool("useMediaStoreScanner", "library"),
        text("themeMode", "appearance") { it in setOf("SYSTEM", "LIGHT", "DARK") },
        bool("dynamicColor", "appearance"),
        bool("artworkColors", "appearance"),
        SettingDef("backgroundDim", SettingType.FLOAT, "appearance") { it is Float && it in 0f..1f },
        text("pureMode", "appearance") { v -> PureMode.entries.any { it.key == v } },
        text("language", "appearance") { it == "en" || it == "el" },
        text("tabOrder", "navigation", 200) { true },
        text("controlColors", "appearance") { it.isEmpty() || ControlColors.parse(it).serialize() == it },
        text("fontChoices", "appearance") { it.isEmpty() || FontChoices.parse(it).serialize() == it },
        text("lyricsAlignment", "lyrics") { v -> LyricsAlignment.entries.any { it.key == v } },
        bool("yourMixCardDismissed", "yourMix"),
        text("mixSettings", "yourMix") { MixSettings.parse(it).serialize() == it },
        bool("autoUpdateCheck", "updates"),
        bool("includeBetaUpdates", "updates"),
        // optional features: activation switches are never restored by default
        bool("widgetsEnabled", "widgets", activation = true),
        bool("replayGainEnabled", "replayGain", activation = true),
        text("replayGainMode", "replayGain") { v -> ReplayGainMode.entries.any { it.key == v } },
        bool("replayGainPreventClipping", "replayGain"),
        bool("equalizerEnabled", "equalizer", activation = true),
        text("eqPreset", "equalizer") { v -> EqPreset.entries.any { it.key == v } },
        text("eqCustom", "equalizer") { EqModel.parseCustom(it) != null },
    )

    private val byKey = all.associateBy { it.key }
    fun def(key: String): SettingDef? = byKey[key]

    /** Settings that exist in the app but are intentionally not part of a settings backup, with the reason. */
    val excluded: List<Pair<String, String>> = listOf(
        "mixSnapshot" to "Regenerated automatically from the library and listening history.",
        "lastUpdateCheckAt" to "Internal timestamp of this installation.",
        "knownNewerVersion" to "Internal state of this installation.",
        "backgroundVersion / customFontVersion" to "Cache keys; the picture and font FILES are backed up instead (.lpbackup only).",
        "autoBackupEnabled / autoBackupFolder / androidBackupCopy and their timestamps" to
            "Device-specific (a folder permission cannot be transferred); the user switches them on per device.",
        "artworkBlur / blurEverywhere / blurStrength" to "Per-device background choice of version 1.7 (the colours switch stays in the format).",
        "gridMode_songs / _albums / _artists / _genres / _playlists" to
            "Per-device list/grid choice of version 1.6.2, kept out of the settings format (schema stays 1).",
        "restoreQueue / keepPlayerAfterClose / fadeBetweenSongs / fadeSeconds / lyricsInPlayer / lyricsFill / hideFileInfoChip / backButtonSide / actionRowStyle / actionRowSide" to
            "Per-device feature switches of version 1.6, kept out of the settings format (schema stays 1).",
        "jamEnabled" to "Optional feature switch of version 1.9.81; each device switches it on itself (it opens a local network server).",
        "library folders" to "Folder access is device-specific and cannot be transferred.",
        "playlists, favourites, history, statistics, hidden songs, artist rules" to
            "Library data, not settings: they travel in library.json of a complete .lpbackup (see LibraryBackupManager).",
    )

    class Validation(val valid: Map<String, Any>, val rejected: Map<String, String>, val unknown: List<String>)

    /** Keeps only known keys with correctly typed, allowed values. Numbers from JSON (Int, Long or Double) are converted to Float. */
    fun validate(raw: Map<String, Any?>): Validation {
        val valid = LinkedHashMap<String, Any>()
        val rejected = LinkedHashMap<String, String>()
        val unknown = ArrayList<String>()
        for ((key, value) in raw) {
            val def = byKey[key]
            if (def == null) { unknown += key; continue }
            val typed: Any? = when (def.type) {
                SettingType.BOOL -> value as? Boolean
                SettingType.STRING -> value as? String
                // JSON numbers may arrive as Int / Long / Double (a hand-edited "1" is as valid as "1.0"); NaN / infinity never are.
                SettingType.FLOAT -> (value as? Number)?.toFloat()?.takeIf { it.isFinite() }
            }
            when {
                typed == null -> rejected[key] = "wrong type"
                !def.validate(typed) -> rejected[key] = "value not allowed"
                else -> valid[key] = typed
            }
        }
        return Validation(valid, rejected, unknown)
    }
}
