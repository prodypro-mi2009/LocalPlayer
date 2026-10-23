package com.localplayer.music.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    private fun tmp() = java.nio.file.Files.createTempDirectory("lpb").toFile()

    private val sample: Map<String, Any> = linkedMapOf(
        "themeMode" to "DARK", "dynamicColor" to false, "backgroundDim" to 0.78f, "language" to "el",
        "pureMode" to "black", "widgetsEnabled" to true, "equalizerEnabled" to true, "eqPreset" to "rock",
        "eqCustom" to "1.0,2.0,0.0,-3.5,4.0", "replayGainMode" to "album", "tabOrder" to "a,b",
        "fontChoices" to "heading=serif", "mixSettings" to com.localplayer.music.data.mix.MixSettings.parse(null).serialize(),
    )

    @Test fun json_roundTrip_and_escapes() {
        val m = linkedMapOf("a" to "x\"y\\z\n\u0001é", "b" to listOf(1, true, null, 2.5), "c" to linkedMapOf("d" to 0.78f))
        val back = MiniJson.parseObject(MiniJson.stringify(m))
        assertEquals("x\"y\\z\n\u0001é", back["a"])
        assertEquals(listOf(1.0, true, null, 2.5), back["b"])
        assertEquals(0.78, (back["c"] as Map<*, *>)["d"] as Double, 0.0)
    }

    @Test fun json_rejectsGarbage() {
        for (bad in listOf("", "{", "{\"a\":}", "[1,]", "{\"a\":1} x", "nul", "\"abc", "{\"a\" 1}")) {
            try { MiniJson.parse(bad); fail("accepted: $bad") } catch (e: MiniJson.JsonException) { }
        }
        try { MiniJson.parse("[".repeat(100)); fail() } catch (e: MiniJson.JsonException) { }
    }

    @Test fun settingsDocument_roundTrip_validatesEverything() {
        val text = BackupFormat.encodeSettings(sample, "1.0.0", 1L, hasBackground = true, hasFont = false, assetsIncluded = false)
        val decoded = BackupFormat.decodeSettings(text)
        val v = SettingsSchema.validate(decoded.settings)
        assertEquals(emptyMap<String, String>(), v.rejected)
        assertEquals(emptyList<String>(), v.unknown)
        assertEquals(sample.keys, v.valid.keys)
        assertEquals(sample, v.valid.toMap())
    }

    @Test fun validation_rejectsBadTypesAndValues_andReportsUnknown() {
        val v = SettingsSchema.validate(
            mapOf("themeMode" to "PURPLE", "dynamicColor" to "yes", "backgroundDim" to 3.0, "language" to "fr",
                "eqCustom" to "1,2,3", "madeUp" to true, "fontChoices" to "heading=comic"),
        )
        assertTrue(v.valid.isEmpty())
        assertEquals(setOf("themeMode", "dynamicColor", "backgroundDim", "language", "eqCustom", "fontChoices"), v.rejected.keys)
        assertEquals(listOf("madeUp"), v.unknown)
    }

    @Test fun decode_rejectsForeignAndFutureFiles() {
        for (bad in listOf("{}", "{\"format\":\"other\"}", "{\"format\":\"localplayer-settings\",\"schema\":99,\"settings\":{}}",
            "{\"format\":\"localplayer-settings\",\"schema\":1}", "not json")) {
            try { BackupFormat.decodeSettings(bad); fail("accepted: $bad") } catch (e: BackupFormat.InvalidBackup) { }
        }
    }

    @Test fun plan_keepsActivationUnlessOptedIn() {
        val v = SettingsSchema.validate(mapOf("widgetsEnabled" to true, "equalizerEnabled" to true, "eqPreset" to "rock", "themeMode" to "DARK"))
        val current = mapOf<String, Any>("widgetsEnabled" to false, "equalizerEnabled" to false, "eqPreset" to "flat", "themeMode" to "DARK")
        val safe = BackupFormat.plan(current, v, restoreActivation = false, backgroundInBackup = false, fontInBackup = false)
        assertEquals(listOf("eqPreset"), safe.changes.map { it.key })
        assertEquals(setOf("widgetsEnabled", "equalizerEnabled"), safe.keptActivation.toSet())
        val opt = BackupFormat.plan(current, v, restoreActivation = true, backgroundInBackup = false, fontInBackup = false)
        assertEquals(setOf("eqPreset", "widgetsEnabled", "equalizerEnabled"), opt.changes.map { it.key }.toSet())
    }

    private fun archive(settingsJson: String, assets: List<BackupArchive.Asset> = emptyList()): ByteArray {
        val o = ByteArrayOutputStream(); BackupArchive.write(o, settingsJson, "1.0.0", 1L, assets); return o.toByteArray()
    }

    @Test fun archive_roundTrip_withAssets() {
        val json = BackupFormat.encodeSettings(sample, "1.0.0", 1L, true, true, true)
        val bg = ByteArray(5000) { it.toByte() }; val font = ByteArray(300) { 7 }
        val bytes = archive(json, listOf(
            BackupArchive.Asset(BackupFormat.BACKGROUND_PATH) { ByteArrayInputStream(bg) },
            BackupArchive.Asset(BackupFormat.FONT_PATH) { ByteArrayInputStream(font) }))
        val dir = tmp()
        val parsed = BackupArchive.read(ByteArrayInputStream(bytes), dir)
        assertArrayEquals(bg, parsed.background!!.readBytes())
        assertArrayEquals(font, parsed.font!!.readBytes())
        assertEquals(sample, SettingsSchema.validate(parsed.settings.settings).valid.toMap())
    }

    @Test fun archive_withoutAssets_hasNoneExtracted() {
        val parsed = BackupArchive.read(ByteArrayInputStream(archive(BackupFormat.encodeSettings(sample, "1", 1L, false, false, true))), tmp())
        assertNull(parsed.background); assertNull(parsed.font)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream(); val z = ZipOutputStream(o)
        for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        z.finish(); return o.toByteArray()
    }

    @Test fun archive_rejectsPathTraversalAndUnknownEntries() {
        val good = archive(BackupFormat.encodeSettings(sample, "1", 1L, false, false, true))
        val dir = tmp()
        for (name in listOf("../evil.txt", "/etc/passwd", "assets/other.bin", "settings/../x")) {
            val bytes = zipOf(name to "x".toByteArray())
            try { BackupArchive.read(ByteArrayInputStream(bytes), dir); fail("accepted $name") } catch (e: BackupFormat.InvalidBackup) { }
        }
        assertEquals(0, dir.list()!!.size) // nothing left behind
        assertNotNull(BackupArchive.read(ByteArrayInputStream(good), dir))
    }

    @Test fun archive_rejectsArbitraryZip_missingParts_andDamage() {
        val dir = tmp()
        for (bytes in listOf(zipOf("readme.txt" to "hi".toByteArray()), zipOf(BackupFormat.SETTINGS to "{}".toByteArray()),
            "not a zip".toByteArray(), ByteArray(0))) {
            try { BackupArchive.read(ByteArrayInputStream(bytes), dir); fail() } catch (e: BackupFormat.InvalidBackup) { }
        }
        // a flipped byte in an asset must fail its checksum
        val json = BackupFormat.encodeSettings(sample, "1", 1L, true, false, true)
        val asset = ByteArray(1000) { 1 }
        val good = archive(json, listOf(BackupArchive.Asset(BackupFormat.BACKGROUND_PATH) { ByteArrayInputStream(asset) }))
        val manifestEntry = BackupArchive.read(ByteArrayInputStream(good), dir) // sanity
        assertNotNull(manifestEntry)
        // rebuild with tampered asset but original manifest
        val zin = java.util.zip.ZipInputStream(ByteArrayInputStream(good)); val parts = LinkedHashMap<String, ByteArray>()
        while (true) { val e = zin.nextEntry ?: break; parts[e.name] = zin.readBytes() }
        parts[BackupFormat.BACKGROUND_PATH] = ByteArray(1000) { 2 }
        try { BackupArchive.read(ByteArrayInputStream(zipOf(*parts.map { it.key to it.value }.toTypedArray())), dir); fail() } catch (e: BackupFormat.InvalidBackup) { }
    }

    @Test fun archive_rejectsOversizeEntry() {
        val json = BackupFormat.encodeSettings(sample, "1", 1L, true, false, true)
        val huge = ByteArray((BackupFormat.MAX_ENTRY_BYTES + 1).toInt())
        val o = ByteArrayOutputStream(); BackupArchive.write(o, json, "1", 1L, listOf(BackupArchive.Asset(BackupFormat.BACKGROUND_PATH) { ByteArrayInputStream(huge) }))
        try { BackupArchive.read(ByteArrayInputStream(o.toByteArray()), tmp()); fail() } catch (e: BackupFormat.InvalidBackup) { }
    }
}
