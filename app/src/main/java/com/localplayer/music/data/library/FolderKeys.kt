package com.localplayer.music.data.library

/**
 * Pure helpers for "which folder is this file in". No Android classes, so everything here is unit tested.
 *
 * A folder is identified by a FOLDER KEY: `"<volume>:<relative path>/"`, for example
 * `external_primary:Music/Rock/` (internal storage) or `1234-abcd:Music/` (an SD card). The root of a
 * volume is `external_primary:`. The key is NOT a file path (the real path is not always readable on
 * modern Android); it is exactly what MediaStore reports (VOLUME_NAME + RELATIVE_PATH) and what the
 * Storage Access Framework's external-storage document ids contain, so both sources produce the same key
 * for the same folder.
 */
object FolderKeys {
    const val PRIMARY_VOLUME = "external_primary"

    /** MediaStore URIs of indexed items ("content://media/external/audio/media/42"). */
    fun isMediaStoreUri(uri: String): Boolean = uri.startsWith("content://media/")

    /** Builds a key from a volume name and a relative path (separators normalised, trailing slash added). */
    fun key(volume: String, relativePath: String): String {
        val vol = normalizeVolume(volume)
        val cleaned = relativePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }.joinToString("/")
        return if (cleaned.isEmpty()) "$vol:" else "$vol:$cleaned/"
    }

    /** MediaStore (Android 10+): VOLUME_NAME and RELATIVE_PATH of an item. Null when MediaStore gave neither. */
    fun fromRelativePath(volumeName: String?, relativePath: String?): String? {
        if (relativePath == null) return null
        return key(volumeName?.takeIf { it.isNotBlank() } ?: PRIMARY_VOLUME, relativePath)
    }

    /**
     * MediaStore before Android 10 only has the deprecated DATA column, a full file path
     * ("/storage/emulated/0/Music/Rock/a.mp3"). Returns the key of the folder that contains the file,
     * or null for a path this app cannot map to a volume.
     */
    fun fromDataPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val volume: String
        val rest: List<String>
        when {
            parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> {
                volume = PRIMARY_VOLUME
                rest = parts.drop(3) // storage/emulated/<user>/...
            }
            parts.size >= 2 && parts[0] == "storage" && parts[1] != "self" -> {
                volume = parts[1]
                rest = parts.drop(2)
            }
            parts.isNotEmpty() && (parts[0] == "sdcard") -> {
                volume = PRIMARY_VOLUME
                rest = parts.drop(1)
            }
            else -> return null
        }
        // The last segment is the file name; the folder is everything before it.
        val folder = if (rest.isEmpty()) emptyList() else rest.dropLast(1)
        return key(volume, folder.joinToString("/"))
    }

    /**
     * An id from the external-storage documents provider ("primary:Music/Rock" for a folder,
     * "primary:Music/Rock/a.mp3" for a file). For a FOLDER id this is the key of that folder itself.
     * Returns null for other providers ("msf:123", "raw:/...", "downloads") that carry no volume/path.
     */
    fun fromFolderDocumentId(documentId: String?): String? {
        if (documentId.isNullOrBlank()) return null
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volumeId = documentId.substring(0, colon)
        if (volumeId == "raw" || volumeId == "msf" || volumeId == "image" || volumeId == "video" || volumeId == "audio") {
            return null
        }
        val volume = if (volumeId == "primary") PRIMARY_VOLUME else volumeId
        return key(volume, documentId.substring(colon + 1))
    }

    /** Volume of a key ("external_primary"). */
    fun volumeOf(key: String): String = key.substringBefore(':')

    /** "Music/Rock" for `external_primary:Music/Rock/`; "" for a volume root. */
    fun relativePathOf(key: String): String = key.substringAfter(':').trimEnd('/')

    /** Last path segment ("Rock"), or "" for a volume root. */
    fun nameOf(key: String): String = relativePathOf(key).substringAfterLast('/')

    /** The key of the containing folder, or null for a volume root. */
    fun parentOf(key: String): String? {
        val relative = relativePathOf(key)
        if (relative.isEmpty()) return null
        return key(volumeOf(key), relative.substringBeforeLast('/', ""))
    }

    /**
     * True when [key] is [root] itself or (when [includeSubfolders]) anywhere below it.
     * Compared on whole path segments by construction: both keys end in '/', so "Music/Rock/" does not
     * cover "Music/Rockabilly/".
     */
    fun covers(root: String, key: String?, includeSubfolders: Boolean = true): Boolean {
        if (key == null) return false
        if (key == root) return true
        return includeSubfolders && key.startsWith(root) && (root.endsWith("/") || root.endsWith(":"))
    }

    private fun normalizeVolume(volume: String): String {
        val lower = volume.trim().lowercase()
        return if (lower == "primary" || lower.isEmpty()) PRIMARY_VOLUME else lower
    }
}
