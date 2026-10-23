package com.localplayer.music.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.localplayer.music.data.AppLanguage
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.ThemeMode
import com.localplayer.music.data.artwork.BackgroundRepository
import com.localplayer.music.data.artwork.CustomFontRepository
import com.localplayer.music.data.effects.EqModel
import com.localplayer.music.data.effects.EqPreset
import com.localplayer.music.data.effects.ReplayGainMode
import com.localplayer.music.data.mix.MixSettings
import com.localplayer.music.data.personalization.ControlColors
import com.localplayer.music.data.personalization.FontChoice
import com.localplayer.music.data.personalization.FontChoices
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.data.personalization.PureMode
import com.localplayer.music.data.update.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** A backup that was read and validated but NOT applied yet. [archive] is null for a portable .json. */
class PreparedImport(
    val plan: ImportPlan,
    val validation: SettingsSchema.Validation,
    val archive: ParsedArchive?,
    val isArchive: Boolean,
    internal val tempDir: File?,
)

sealed interface ApplyResult {
    data class Success(val changed: Int, val background: Boolean, val font: Boolean) : ApplyResult
    data class Failed(val reason: String, val restoredPrevious: Boolean) : ApplyResult
}

/**
 * Glue between the pure backup code and the real storage (AppSettings + the two asset files). Idle unless the user
 * picks an export or import: nothing here runs at startup.
 */
class SettingsBackupManager(
    private val context: Context,
    private val settings: AppSettings,
    private val background: BackgroundRepository,
    private val font: CustomFontRepository,
) {
    // ---- settings <-> map -------------------------------------------------------------------------------------

    /** The current value of every backed-up setting. */
    fun snapshot(): Map<String, Any> = linkedMapOf<String, Any>().apply {
        put("useMediaStoreScanner", settings.useMediaStoreScanner.value)
        put("themeMode", settings.themeMode.value.name)
        put("dynamicColor", settings.dynamicColor.value)
        put("artworkColors", settings.artworkColors.value)
        put("backgroundDim", settings.backgroundDim.value)
        put("pureMode", settings.pureMode.value.key)
        put("language", settings.language.value.tag)
        put("tabOrder", settings.tabOrder.value.orEmpty())
        put("controlColors", settings.controlColors.value.serialize().orEmpty())
        put("fontChoices", settings.fontChoices.value.serialize().orEmpty())
        put("lyricsAlignment", settings.lyricsAlignment.value.key)
        put("yourMixCardDismissed", settings.yourMixCardDismissed.value)
        put("mixSettings", settings.mixSettings.value.serialize())
        put("autoUpdateCheck", settings.autoUpdateCheck.value)
        put("widgetsEnabled", settings.widgetsEnabled.value)
        put("replayGainEnabled", settings.replayGainEnabled.value)
        put("replayGainMode", settings.replayGainMode.value.key)
        put("replayGainPreventClipping", settings.replayGainPreventClipping.value)
        put("equalizerEnabled", settings.equalizerEnabled.value)
        put("eqPreset", settings.eqPreset.value.key)
        put("eqCustom", EqModel.serializeCustom(settings.eqCustom.value))
    }

    private fun write(key: String, value: Any) {
        when (key) {
            "useMediaStoreScanner" -> settings.setUseMediaStoreScanner(value as Boolean)
            "themeMode" -> settings.setThemeMode(ThemeMode.valueOf(value as String))
            "dynamicColor" -> settings.setDynamicColor(value as Boolean)
            "artworkColors" -> settings.setArtworkColors(value as Boolean)
            "backgroundDim" -> settings.setBackgroundDim(value as Float)
            "pureMode" -> settings.setPureMode(PureMode.fromKey(value as String))
            "language" -> settings.setLanguage(AppLanguage.fromTag(value as String))
            "tabOrder" -> settings.setTabOrder((value as String).ifEmpty { null })
            "controlColors" -> settings.setControlColors(ControlColors.parse(value as String))
            "fontChoices" -> settings.setFontChoices(FontChoices.parse(value as String))
            "lyricsAlignment" -> settings.setLyricsAlignment(LyricsAlignment.fromKey(value as String))
            "yourMixCardDismissed" -> settings.setYourMixCardDismissed(value as Boolean)
            "mixSettings" -> settings.setMixSettings(MixSettings.parse(value as String))
            "autoUpdateCheck" -> settings.setAutoUpdateCheck(value as Boolean)
            "widgetsEnabled" -> settings.setWidgetsEnabled(value as Boolean)
            "replayGainEnabled" -> settings.setReplayGainEnabled(value as Boolean)
            "replayGainMode" -> settings.setReplayGainMode(ReplayGainMode.fromKey(value as String))
            "replayGainPreventClipping" -> settings.setReplayGainPreventClipping(value as Boolean)
            "equalizerEnabled" -> settings.setEqualizerEnabled(value as Boolean)
            "eqPreset" -> settings.setEqPreset(EqPreset.fromKey(value as String))
            "eqCustom" -> settings.setEqCustom(EqModel.parseCustom(value as String)!!)
            else -> error("Unknown setting $key")
        }
    }

    // ---- export -----------------------------------------------------------------------------------------------

    /** Writes the portable JSON or the complete .lpbackup to [target], re-reads it, and deletes it again if it is not valid. */
    suspend fun export(target: Uri, complete: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val bg = if (complete) background.fileForBackup() else null
            val ft = if (complete) font.fileForBackup() else null
            val json = BackupFormat.encodeSettings(
                snapshot(), AppInfo.versionName(context), System.currentTimeMillis(),
                hasBackground = background.fileForBackup() != null, hasFont = font.fileForBackup() != null, assetsIncluded = complete,
            )
            val out = resolver.openOutputStream(target, "wt") ?: error("Cannot open the destination")
            out.use {
                if (complete) {
                    val assets = listOfNotNull(
                        bg?.let { f -> BackupArchive.Asset(BackupFormat.BACKGROUND_PATH) { f.inputStream() } },
                        ft?.let { f -> BackupArchive.Asset(BackupFormat.FONT_PATH) { f.inputStream() } },
                    )
                    BackupArchive.write(it, json, AppInfo.versionName(context), System.currentTimeMillis(), assets)
                } else {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
                it.flush()
            }
            // verify what was really written
            val temp = File(context.cacheDir, "backup-verify").also { it.mkdirs() }
            try {
                resolver.openInputStream(target)!!.use { input ->
                    if (complete) BackupArchive.read(input, temp) else BackupFormat.decodeSettings(input.readBytes().toString(Charsets.UTF_8))
                }
            } finally {
                BackupArchive.cleanup(temp)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }   // never leave a half-written file behind
            Result.failure(e)
        }
    }

    // ---- import: read + validate + plan (changes nothing) -----------------------------------------------------

    suspend fun prepare(source: Uri, restoreActivation: Boolean): Result<PreparedImport> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "backup-import").also { BackupArchive.cleanup(it); it.mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw BackupFormat.InvalidBackup("Cannot open the file")
            val bytes = input.use { readLimited(it) }
            val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val archive = if (isZip) BackupArchive.read(bytes.inputStream(), temp) else null
            val doc = archive?.settings ?: BackupFormat.decodeSettings(bytes.toString(Charsets.UTF_8))
            val validation = SettingsSchema.validate(doc.settings)
            if (validation.valid.isEmpty()) throw BackupFormat.InvalidBackup("The file contains no usable settings")
            val plan = planFor(validation, restoreActivation, archive?.background != null, archive?.font != null)
            Result.success(PreparedImport(plan, validation, archive, isZip, temp))
        } catch (e: BackupFormat.InvalidBackup) {
            BackupArchive.cleanup(temp); Result.failure(e)
        } catch (e: Exception) {
            BackupArchive.cleanup(temp); Result.failure(BackupFormat.InvalidBackup("The file could not be read: ${e.message}"))
        }
    }

    fun planFor(v: SettingsSchema.Validation, restoreActivation: Boolean, bg: Boolean, ft: Boolean): ImportPlan =
        BackupFormat.plan(snapshot(), v, restoreActivation, bg, ft)

    private fun readLimited(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > BackupFormat.MAX_TOTAL_BYTES) throw BackupFormat.InvalidBackup("The file is larger than a settings backup can be")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ---- import: apply (after the user confirmed) -------------------------------------------------------------

    /**
     * Applies [prepared] with the plan for [restoreActivation]. Assets are restored first (each one validated),
     * then the settings. The result is read back and compared; on any mismatch or error the previous settings
     * and asset files are put back.
     */
    suspend fun apply(prepared: PreparedImport, restoreActivation: Boolean): ApplyResult = withContext(Dispatchers.IO) {
        val before = snapshot()
        val oldBg = background.fileForBackup()?.let { f -> File(context.cacheDir, "undo-bg").also { f.copyTo(it, overwrite = true) } }
        val oldFont = font.fileForBackup()?.let { f -> File(context.cacheDir, "undo-font").also { f.copyTo(it, overwrite = true) } }
        var restoredBg = false
        var restoredFont = false
        try {
            val archive = prepared.archive
            archive?.background?.let { if (!background.restoreFrom(it)) error("The background picture in the backup could not be used"); restoredBg = true }
            archive?.font?.let { if (!font.restoreFrom(it)) error("The font in the backup could not be used"); restoredFont = true }

            val values = LinkedHashMap(prepared.validation.valid)
            // A font choice that points to a custom font nobody has must not be restored (it would silently fall back).
            values["fontChoices"]?.let { raw ->
                if (!font.exists()) {
                    var fc = FontChoices.parse(raw as String)
                    com.localplayer.music.data.personalization.FontCategory.entries.forEach { c -> if (fc[c] == FontChoice.CUSTOM) fc = fc.with(c, FontChoice.DEFAULT) }
                    values["fontChoices"] = fc.serialize().orEmpty()
                }
            }
            val expected = HashMap<String, Any>()
            for ((key, value) in values) {
                val def = SettingsSchema.def(key) ?: continue
                if (def.activation && !restoreActivation) continue
                write(key, value)
                expected[key] = if (key == "useMediaStoreScanner") (value as Boolean) && AppSettings.mediaStoreScannerSupported() else value
            }
            val after = snapshot()
            val wrong = expected.filter { (k, v) -> after[k] != v }.keys
            if (wrong.isNotEmpty()) error("Verification failed for: ${wrong.joinToString()}")
            ApplyResult.Success(expected.count { (k, v) -> before[k] != v }, restoredBg, restoredFont)
        } catch (e: Exception) {
            val ok = rollback(before, oldBg, oldFont, restoredBg, restoredFont)
            ApplyResult.Failed(e.message ?: "unknown error", ok)
        } finally {
            oldBg?.delete(); oldFont?.delete()
            prepared.tempDir?.let { BackupArchive.cleanup(it) }
        }
    }

    private suspend fun rollback(before: Map<String, Any>, oldBg: File?, oldFont: File?, bgTouched: Boolean, fontTouched: Boolean): Boolean = try {
        for ((k, v) in before) write(k, v)
        if (bgTouched) { if (oldBg != null) background.restoreFrom(oldBg) else background.reset() }
        if (fontTouched) { if (oldFont != null) font.restoreFrom(oldFont) else font.remove() }
        snapshot() == before
    } catch (e: Exception) {
        false
    }

    fun discard(prepared: PreparedImport) { prepared.tempDir?.let { BackupArchive.cleanup(it) } }
}
