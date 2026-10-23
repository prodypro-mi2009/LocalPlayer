package com.localplayer.music.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** An extracted, verified .lpbackup / .zip backup. Asset files live in a temporary directory chosen by the caller. */
class ParsedArchive(
    val settings: BackupFormat.DecodedSettings,
    val background: File?,
    val font: File?,
)

/**
 * Reads and writes the ".lpbackup" archive (a ZIP):
 *
 *   manifest.json                       format, schema, app version, time, list of assets with size + SHA-256
 *   settings.json                       the settings document (same as the portable .json)
 *   assets/background/background.jpg    only when the user has a custom background
 *   assets/fonts/custom-font.ttf        only when the user imported a font
 *
 * Reading accepts ONLY these four entry names (no path can escape the target directory), bounds every entry and the
 * total size, and verifies every asset against the manifest. Pure java.util.zip, so it is tested on the JVM.
 */
object BackupArchive {

    class Asset(val path: String, val open: () -> InputStream)

    fun write(out: OutputStream, settingsJson: String, appVersion: String, exportedAtMs: Long, assets: List<Asset>) {
        val zip = ZipOutputStream(out)
        val infos = ArrayList<Map<String, Any>>()
        val assetBytes = ArrayList<Pair<String, ByteArray>>()
        for (a in assets) {
            require(a.path in BackupFormat.ALLOWED_ENTRIES) { "Unexpected asset path ${a.path}" }
            val bytes = a.open().use { it.readBytes() }
            infos += linkedMapOf("path" to a.path, "size" to bytes.size.toLong(), "sha256" to sha256(bytes))
            assetBytes += a.path to bytes
        }
        val manifest = MiniJson.stringify(
            linkedMapOf(
                "format" to BackupFormat.ARCHIVE_FORMAT,
                "schema" to BackupFormat.SCHEMA,
                "appVersion" to appVersion,
                "exportedAt" to exportedAtMs,
                "settingsSha256" to sha256(settingsJson.toByteArray()),
                "assets" to infos,
            ),
        )
        put(zip, BackupFormat.MANIFEST, manifest.toByteArray())
        put(zip, BackupFormat.SETTINGS, settingsJson.toByteArray())
        for ((path, bytes) in assetBytes) put(zip, path, bytes)
        zip.finish()
        zip.flush()
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    /** Throws [BackupFormat.InvalidBackup] for anything that is not a complete, intact LocalPlayer backup. */
    @Suppress("UNCHECKED_CAST")
    fun read(input: InputStream, tempDir: File): ParsedArchive {
        tempDir.mkdirs()
        val texts = HashMap<String, String>()
        val files = HashMap<String, File>()
        val sums = HashMap<String, String>()
        var total = 0L
        var count = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (++count > 8) throw BackupFormat.InvalidBackup("The archive has too many entries")
                    val name = entry.name
                    if (name !in BackupFormat.ALLOWED_ENTRIES) throw BackupFormat.InvalidBackup("Unexpected entry in the archive: $name")
                    if (name in texts || name in files) throw BackupFormat.InvalidBackup("Duplicate entry: $name")
                    val limit = if (name == BackupFormat.MANIFEST || name == BackupFormat.SETTINGS) BackupFormat.MAX_SETTINGS_BYTES else BackupFormat.MAX_ENTRY_BYTES
                    val digest = MessageDigest.getInstance("SHA-256")
                    val isText = name == BackupFormat.MANIFEST || name == BackupFormat.SETTINGS
                    val sink = if (isText) java.io.ByteArrayOutputStream() else File(tempDir, "asset-${files.size}").also { files[name] = it }.outputStream()
                    var size = 0L
                    sink.use { o ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n; total += n
                            if (size > limit || total > BackupFormat.MAX_TOTAL_BYTES) throw BackupFormat.InvalidBackup("The archive is larger than allowed")
                            digest.update(buf, 0, n)
                            o.write(buf, 0, n)
                        }
                        if (isText) texts[name] = String((o as java.io.ByteArrayOutputStream).toByteArray(), Charsets.UTF_8)
                    }
                    sums[name] = hex(digest.digest())
                }
            }
            val manifestText = texts[BackupFormat.MANIFEST] ?: throw BackupFormat.InvalidBackup("manifest.json is missing")
            val settingsText = texts[BackupFormat.SETTINGS] ?: throw BackupFormat.InvalidBackup("settings.json is missing")
            val manifest = try { MiniJson.parseObject(manifestText) } catch (e: MiniJson.JsonException) { throw BackupFormat.InvalidBackup("manifest.json is damaged") }
            if (manifest["format"] != BackupFormat.ARCHIVE_FORMAT) throw BackupFormat.InvalidBackup("This archive is not a LocalPlayer backup")
            val schema = (manifest["schema"] as? Double)?.toInt() ?: throw BackupFormat.InvalidBackup("Missing schema version")
            if (schema < 1 || schema > BackupFormat.SCHEMA) throw BackupFormat.InvalidBackup("Unsupported backup version $schema")
            if (manifest["settingsSha256"] != sums[BackupFormat.SETTINGS]) throw BackupFormat.InvalidBackup("settings.json does not match its checksum (damaged file)")
            val declared = (manifest["assets"] as? List<Any?>).orEmpty().map { it as? Map<String, Any?> ?: throw BackupFormat.InvalidBackup("Bad asset list") }
            val declaredPaths = declared.map { it["path"] as? String }
            if (declaredPaths.toSet() != files.keys) throw BackupFormat.InvalidBackup("The asset list does not match the archive contents")
            for (d in declared) {
                val path = d["path"] as String
                if (d["sha256"] != sums[path] || (d["size"] as? Double)?.toLong() != files.getValue(path).length())
                    throw BackupFormat.InvalidBackup("$path does not match its checksum (damaged file)")
            }
            return ParsedArchive(BackupFormat.decodeSettings(settingsText), files[BackupFormat.BACKGROUND_PATH], files[BackupFormat.FONT_PATH])
        } catch (e: BackupFormat.InvalidBackup) {
            cleanup(tempDir); throw e
        } catch (e: java.util.zip.ZipException) {
            cleanup(tempDir); throw BackupFormat.InvalidBackup("This file is not a readable archive")
        } catch (e: Exception) {
            cleanup(tempDir); throw BackupFormat.InvalidBackup("The backup could not be read: ${e.message}")
        }
    }

    fun cleanup(dir: File) { dir.listFiles()?.forEach { it.delete() } }

    private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
