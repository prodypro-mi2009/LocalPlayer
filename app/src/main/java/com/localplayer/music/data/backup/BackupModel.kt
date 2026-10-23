package com.localplayer.music.data.backup

/** One difference between the current settings and the backup, shown in the import preview. */
data class SettingChange(val key: String, val group: String, val from: Any?, val to: Any)

/** What an import would do. Built before anything is written; the user confirms it. */
data class ImportPlan(
    val changes: List<SettingChange>,
    /** Activation switches of optional features that were NOT applied (kept as on this installation). */
    val keptActivation: List<String>,
    val rejected: Map<String, String>,
    val unknown: List<String>,
    val restoreBackground: Boolean,
    val restoreFont: Boolean,
)

object BackupFormat {
    const val SETTINGS_FORMAT = "localplayer-settings"
    const val ARCHIVE_FORMAT = "localplayer-backup"
    const val SCHEMA = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val BACKGROUND_PATH = "assets/background/background.jpg"
    const val FONT_PATH = "assets/fonts/custom-font.ttf"
    val ALLOWED_ENTRIES = setOf(MANIFEST, SETTINGS, BACKGROUND_PATH, FONT_PATH)
    const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 48L * 1024 * 1024
    const val MAX_SETTINGS_BYTES = 256L * 1024

    class InvalidBackup(message: String) : Exception(message)

    /** The settings document used both for the portable .json and as settings.json inside the archive. */
    fun encodeSettings(
        values: Map<String, Any>,
        appVersion: String,
        exportedAtMs: Long,
        hasBackground: Boolean,
        hasFont: Boolean,
        assetsIncluded: Boolean,
    ): String = MiniJson.stringify(
        linkedMapOf(
            "format" to SETTINGS_FORMAT,
            "schema" to SCHEMA,
            "appVersion" to appVersion,
            "exportedAt" to exportedAtMs,
            "settings" to LinkedHashMap(values.toSortedMap()),
            "assets" to linkedMapOf(
                "background" to linkedMapOf("used" to hasBackground, "included" to (hasBackground && assetsIncluded)),
                "font" to linkedMapOf("used" to hasFont, "included" to (hasFont && assetsIncluded)),
            ),
        ),
    )

    class DecodedSettings(val schema: Int, val appVersion: String, val settings: Map<String, Any?>)

    @Suppress("UNCHECKED_CAST")
    fun decodeSettings(text: String): DecodedSettings {
        val root = try { MiniJson.parseObject(text) } catch (e: MiniJson.JsonException) { throw InvalidBackup("Not a valid JSON file: ${e.message}") }
        if (root["format"] != SETTINGS_FORMAT) throw InvalidBackup("This is not a LocalPlayer settings file")
        val schema = (root["schema"] as? Double)?.toInt() ?: throw InvalidBackup("Missing schema version")
        if (schema < 1 || schema > SCHEMA) throw InvalidBackup("Unsupported backup version $schema (this app understands up to $SCHEMA)")
        val settings = root["settings"] as? Map<String, Any?> ?: throw InvalidBackup("Missing settings")
        return DecodedSettings(schema, root["appVersion"] as? String ?: "", settings)
    }

    /**
     * Compares [current] (what this installation has now) with the validated [backup].
     * Activation switches are skipped unless [restoreActivation] is true.
     */
    fun plan(
        current: Map<String, Any>,
        validation: SettingsSchema.Validation,
        restoreActivation: Boolean,
        backgroundInBackup: Boolean,
        fontInBackup: Boolean,
    ): ImportPlan {
        val changes = ArrayList<SettingChange>()
        val kept = ArrayList<String>()
        for ((key, value) in validation.valid) {
            val def = SettingsSchema.def(key) ?: continue
            if (def.activation && !restoreActivation) {
                if (current[key] != value) kept += key
                continue
            }
            if (current[key] != value) changes += SettingChange(key, def.group, current[key], value)
        }
        return ImportPlan(changes, kept, validation.rejected, validation.unknown, backgroundInBackup, fontInBackup)
    }
}
