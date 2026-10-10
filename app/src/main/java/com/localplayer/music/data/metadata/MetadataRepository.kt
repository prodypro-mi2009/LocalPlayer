package com.localplayer.music.data.metadata

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.db.TrackEntity
import com.localplayer.music.data.library.TrackIndexing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

sealed interface WriteAccess {
    data object Granted : WriteAccess
    /** Android must ask the user (system dialog) before this file may be modified. */
    data class NeedsConsent(val intentSender: IntentSender) : WriteAccess
    /** Android 8-9 only: the WRITE_EXTERNAL_STORAGE runtime permission is needed. */
    data object NeedsLegacyPermission : WriteAccess
    data class Denied(val kind: FailureKind, val detail: String) : WriteAccess
}

data class LoadedMetadata(
    val metadata: TrackMetadata,
    val capability: WriteCapability,
    /** True when [metadata] was read from the file itself; false for read-only formats shown from the index. */
    val fromFile: Boolean,
    val displayName: String,
)

/**
 * Reads and writes the REAL tags of an audio file that is only reachable through a MediaStore URI.
 *
 * Write procedure (the original is never deleted before a verified replacement exists):
 *   1. copy the original to a private backup (src.bin) and to a working copy with the right extension
 *   2. edit ONLY the requested fields in the working copy (FileTagEngine), re-read and verify it
 *   3. write the working copy over the original through the system-granted write access
 *   4. copy the result back out of the real file, compare it byte-for-byte with the working copy
 *      and re-read the tags from it: that re-read is the verification
 *   5. on ANY failure after step 3, put the backup back and report the failure
 * The audio stream is never decoded or re-encoded.
 */
class MetadataRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val searchIndex: SearchIndex,
) {
    private val resolver = context.contentResolver
    private val tracks = database.trackDao()

    // --- loading -----------------------------------------------------------------------------

    suspend fun load(trackId: Long): LoadedMetadata = withContext(Dispatchers.IO) {
        val entity = tracks.getEntity(trackId)
            ?: throw MetadataException(FailureKind.FILE_UNAVAILABLE, "This track is no longer in the library.")
        val dir = newWorkDir()
        try {
            val source = File(dir, "source.bin")
            copyFromUri(Uri.parse(entity.uri), source)
            val container = AudioSniffer.sniff(source, entity.displayName)
            if (!container.tagWritable) {
                return@withContext LoadedMetadata(
                    metadata = fromIndex(entity, container),
                    capability = WriteCapability.forContainer(container),
                    fromFile = false,
                    displayName = entity.displayName,
                )
            }
            val working = File(dir, "read.${container.workingExtension}")
            if (!source.renameTo(working)) throw IOException("Could not prepare a working copy")
            LoadedMetadata(
                metadata = FileTagEngine.read(working, container),
                capability = WriteCapability.forContainer(container),
                fromFile = true,
                displayName = entity.displayName,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- write access ------------------------------------------------------------------------

    suspend fun checkWriteAccess(uri: Uri): WriteAccess = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            return@withContext if (granted) WriteAccess.Granted else WriteAccess.NeedsLegacyPermission
        }
        try {
            // Opening for write (without truncating) is the real test of whether we may modify the item.
            val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: return@withContext WriteAccess.Denied(FailureKind.FILE_UNAVAILABLE, "The file could not be opened.")
            descriptor.close()
            WriteAccess.Granted
        } catch (e: FileNotFoundException) {
            WriteAccess.Denied(FailureKind.FILE_UNAVAILABLE, e.message ?: "The file is not available.")
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    WriteAccess.NeedsConsent(MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender)
                e is RecoverableSecurityException ->
                    WriteAccess.NeedsConsent(e.userAction.actionIntent.intentSender)
                else -> WriteAccess.Denied(FailureKind.WRITE_PERMISSION_DENIED, e.message ?: "Write access denied.")
            }
        }
    }

    // --- saving ------------------------------------------------------------------------------

    /**
     * Writes [changes] into the real file and returns the metadata re-read from that file.
     * Throws [MetadataException] on any failure; the original file is restored when needed.
     */
    suspend fun save(trackId: Long, changes: MetadataChanges): TrackMetadata = withContext(Dispatchers.IO) {
        val entity = tracks.getEntity(trackId)
            ?: throw MetadataException(FailureKind.FILE_UNAVAILABLE, "This track is no longer in the library.")
        if (changes.isEmpty()) throw MetadataException(FailureKind.INVALID_INPUT, "Nothing to save.")
        for ((field, value) in changes) {
            MetadataValidation.check(field, value)?.let { throw MetadataException(FailureKind.INVALID_INPUT, it) }
        }

        val uri = Uri.parse(entity.uri)
        val dir = newWorkDir()
        var keepBackup = false
        try {
            requireFreeSpace(uri)

            // 1. backup + working copy
            val backup = File(dir, "backup.bin")
            copyFromUri(uri, backup)
            val container = AudioSniffer.sniff(backup, entity.displayName)
            if (!container.tagWritable) {
                throw MetadataException(
                    FailureKind.UNSUPPORTED_FORMAT,
                    "Metadata editing is not supported safely for ${container.label}.",
                )
            }
            // The size query before the backup can be missing or stale; now the real size is known.
            requireSpace(StorageEstimate.requiredAfterBackup(backup.length()), dir)
            val working = File(dir, "work.${container.workingExtension}")
            backup.copyTo(working, overwrite = true)
            val audioBefore = AudioPayload.sha256(backup, container)

            // 2. edit only the requested fields, then check the working copy before touching the original
            val before = FileTagEngine.read(working, container)
            FileTagEngine.write(working, container, changes)
            val editedCheck = FileTagEngine.read(working, container)
            FileTagEngine.verify(before, editedCheck, changes)?.let {
                throw MetadataException(FailureKind.VERIFICATION_FAILED, it)
            }
            // The audio data itself must be byte-for-byte what it was (only the tag area may differ).
            // Checked on the working copy, so a failure here never touches the original file.
            if (audioBefore != null && AudioPayload.sha256(working, container) != audioBefore) {
                throw MetadataException(
                    FailureKind.VERIFICATION_FAILED,
                    "The audio data would have changed. The file was not modified.",
                )
            }
            val workingDigest = sha256(working)

            // 3. replace the original (through the system-granted write access)
            try {
                writeBack(uri, working)
            } catch (e: Exception) {
                keepBackup = !restore(uri, backup)
                throw asMetadataException(e, keepBackup, dir)
            }

            // 4. read it back OUT OF THE REAL FILE and verify
            val verifyCopy = File(dir, "verify.${container.workingExtension}")
            val actual: TrackMetadata
            try {
                copyFromUri(uri, verifyCopy)
                if (sha256(verifyCopy) != workingDigest) {
                    throw MetadataException(
                        FailureKind.VERIFICATION_FAILED,
                        "The file on the device does not match what was written.",
                    )
                }
                actual = FileTagEngine.read(verifyCopy, container)
                FileTagEngine.verify(before, actual, changes)?.let {
                    throw MetadataException(FailureKind.VERIFICATION_FAILED, it)
                }
            } catch (e: Exception) {
                keepBackup = !restore(uri, backup)
                throw asMetadataException(e, keepBackup, dir)
            }

            // 5. only now: refresh the cache from what the file really contains
            refreshCache(entity, uri, actual)
            actual
        } finally {
            if (!keepBackup) dir.deleteRecursively()
            cleanStaleWorkDirs()
        }
    }

    // --- cache refresh (file -> Room, never the other way round) -------------------------------

    private suspend fun refreshCache(entity: TrackEntity, uri: Uri, meta: TrackMetadata) {
        val (modified, size) = currentStamp(uri) ?: (entity.lastModified to entity.sizeBytes)
        val groups = tracks.distinctAlbumArtists()
            .mapNotNull { com.localplayer.music.data.library.TextNormalizer.groupKey(it) }.toSet()
        val override = tracks.allOverrides().firstOrNull { it.trackUri == entity.uri }?.primaryArtist
        val title = meta.title ?: entity.displayName.substringBeforeLast('.')
        val derived = TrackIndexing.derive(
            title = title,
            artist = meta.artist,
            albumArtist = meta.albumArtist,
            album = meta.album,
            genre = meta.genre,
            knownGroupNames = groups,
            primaryOverride = override,
            structuredArtists = meta.artists,
        )
        val updated = entity.copy(
            title = title,
            artist = meta.artist,
            albumArtist = meta.albumArtist,
            album = meta.album,
            genre = meta.genre,
            year = TrackIndexing.parseYear(meta.year),
            trackNumber = TrackIndexing.parseLeadingInt(meta.trackNumber),
            discNumber = TrackIndexing.parseLeadingInt(meta.discNumber),
            hasArtwork = meta.hasArtwork,
            lastModified = modified,
            sizeBytes = size,
            isAvailable = true,
            scannedAt = System.currentTimeMillis(),
            primaryArtist = derived.primaryArtist,
            primaryArtistKey = derived.primaryArtistKey,
            autoPrimaryArtist = derived.autoPrimaryArtist,
            albumKey = derived.albumKey,
            albumGroupKey = derived.albumGroupKey,
            genreKey = derived.genreKey,
            titleSort = derived.titleSort,
            artistSort = derived.artistSort,
            albumSort = derived.albumSort,
            indexVersion = TrackIndexing.INDEX_VERSION,
        )
        tracks.upsert(updated)
        searchIndex.upsert(
            listOf(
                SearchIndex.Row(
                    trackId = updated.id,
                    title = updated.title,
                    artist = updated.artist,
                    album = updated.album,
                    genre = updated.genre,
                    year = updated.year,
                    primaryArtist = updated.primaryArtist,
                ),
            ),
        )
    }

    /** Modification time (ms) and size MediaStore reports now for the item, or null. */
    private fun currentStamp(uri: Uri): Pair<Long, Long>? = try {
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) * 1000L to c.getLong(1) else null
        }
    } catch (e: Exception) {
        null
    }

    // --- file helpers ------------------------------------------------------------------------

    private fun fromIndex(entity: TrackEntity, container: AudioContainer) = TrackMetadata(
        container = container,
        title = entity.title,
        artist = entity.artist,
        album = entity.album,
        albumArtist = entity.albumArtist,
        genre = entity.genre,
        year = entity.year?.toString(),
        trackNumber = entity.trackNumber?.toString(),
        discNumber = entity.discNumber?.toString(),
        durationMs = entity.durationMs,
    )

    private fun copyFromUri(uri: Uri, target: File) {
        try {
            val input: InputStream = resolver.openInputStream(uri)
                ?: throw MetadataException(FailureKind.FILE_UNAVAILABLE, "The file could not be opened.")
            input.use { source -> target.outputStream().use { out -> copy(source, out) } }
        } catch (e: MetadataException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw MetadataException(FailureKind.FILE_UNAVAILABLE, e.message ?: "The file is not available.", e)
        } catch (e: SecurityException) {
            throw MetadataException(FailureKind.WRITE_PERMISSION_DENIED, e.message ?: "Access denied.", e)
        } catch (e: IOException) {
            throw asMetadataException(e, false, null)
        }
    }

    /** Overwrites the real file with [source] and forces the data to storage before returning. */
    private fun writeBack(uri: Uri, source: File) {
        val descriptor = resolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("The file could not be opened for writing.")
        descriptor.use { pfd ->
            java.io.FileOutputStream(pfd.fileDescriptor).use { out ->
                source.inputStream().use { input -> copy(input, out) }
                out.flush()
                out.fd.sync()
            }
        }
    }

    /** Puts the original bytes back. Returns true when the original was restored. */
    private fun restore(uri: Uri, backup: File): Boolean = try {
        writeBack(uri, backup)
        true
    } catch (e: Exception) {
        false
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireFreeSpace(uri: Uri) {
        val size = try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
        requireSpace(StorageEstimate.requiredBeforeEdit(size), null)
        // The original is rewritten in place on its own volume, which can differ from the app cache.
        try {
            val media = StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
            val needed = StorageEstimate.requiredOnMediaVolume(size)
            if (media < needed) throw insufficient(needed)
        } catch (e: MetadataException) {
            throw e
        } catch (e: Exception) {
            // The volume could not be inspected; the cache check above still applies.
        }
    }

    private fun requireSpace(needed: Long, dir: File?) {
        val available = StatFs(context.cacheDir.path).availableBytes
        if (available < needed) {
            dir?.deleteRecursively()
            throw insufficient(needed)
        }
    }

    private fun insufficient(needed: Long) = MetadataException(
        FailureKind.INSUFFICIENT_STORAGE,
        "Not enough free space for a safe edit (needs about ${needed / (1024 * 1024) + 1} MB).",
    )

    private fun asMetadataException(e: Exception, backupKept: Boolean, dir: File?): MetadataException {
        if (e is MetadataException && !backupKept) return e
        val message = e.message ?: e.javaClass.simpleName
        val kind = when {
            e is MetadataException -> e.kind
            e is SecurityException -> FailureKind.WRITE_PERMISSION_DENIED
            e is FileNotFoundException -> FailureKind.FILE_UNAVAILABLE
            message.contains("space", ignoreCase = true) || message.contains("ENOSPC") -> FailureKind.INSUFFICIENT_STORAGE
            else -> FailureKind.WRITER_ERROR
        }
        val detail = if (backupKept && dir != null) {
            "$message. The original could not be restored automatically; a backup copy was kept in ${dir.path}."
        } else {
            message
        }
        return MetadataException(kind, detail, e)
    }

    private fun workRoot() = File(context.cacheDir, "metadata-work").apply { mkdirs() }

    private fun newWorkDir() = File(workRoot(), UUID.randomUUID().toString()).apply { mkdirs() }

    /** Old leftovers (older than a week) only; a recent backup of a failed restore is never deleted here. */
    private fun cleanStaleWorkDirs() {
        val limit = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        workRoot().listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
    }
}
