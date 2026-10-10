package com.localplayer.music.data.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.localplayer.music.data.db.MediaKind
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.db.TrackDao
import com.localplayer.music.data.db.TrackEntity
import com.localplayer.music.util.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * NORMAL scanner (accurate). AUDIO ONLY.
 *
 * MediaStore is used to enumerate the audio files (.mp3 .flac .m4a .aac) and their URIs; the tags of
 * every new or changed file are then read from the FILE itself. The audio file is the source of truth;
 * the Room row is a cache rebuilt from it. For the metadata-less MediaStore-only mode see
 * [MediaStoreFastScanner].
 */
class LibraryScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val metadataReader: MetadataReader,
    private val searchIndex: SearchIndex,
    private val coordinator: ScanCoordinator,
) {
    private val readDispatcher = Dispatchers.IO.limitedParallelism(3)

    suspend fun scanAll() = coordinator.run { runPass() }

    private data class FoundMedia(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val lastModified: Long,
        val dateAdded: Long,
        val size: Long,
    )

    private suspend fun runPass() {
        coordinator.begin()
        if (!MediaAccess.audioGranted(context)) return
        val found = withContext(Dispatchers.IO) { queryAudio() }
        if (found == null) {
            coordinator.fail(ScanFailureKind.QUERY_FAILED)
            return
        }
        coordinator.update { it.copy(discovered = found.size) }
        sync(found)
    }

    private suspend fun sync(found: List<FoundMedia>) {
        val existing = trackDao.getSyncInfo().associateBy { it.uri }
        val foundUris = HashSet<String>(found.size * 2)
        for (file in found) foundUris.add(file.uri.toString())

        // Files MediaStore no longer lists, and anything left over from the old video-aware versions.
        val deletedIds = existing.values
            .filter { it.uri !in foundUris || it.mediaType != MediaKind.AUDIO }
            .map { it.id }
        for (chunk in deletedIds.chunked(SQL_CHUNK)) trackDao.deleteByIds(chunk)
        searchIndex.delete(deletedIds)

        // New files, changed files, files flagged unavailable, and rows built by older index rules
        // or by the fast scanner (these are re-read from the file here).
        val toProcess = found.filter { file ->
            val old = existing[file.uri.toString()]
            old == null ||
                old.mediaType != MediaKind.AUDIO ||
                !old.isAvailable ||
                old.lastModified != file.lastModified ||
                old.sizeBytes != file.size ||
                old.indexVersion != TrackIndexing.INDEX_VERSION
        }
        coordinator.update { it.copy(toProcess = toProcess.size) }
        if (toProcess.isEmpty()) return

        val groupNames = trackDao.distinctAlbumArtists().mapNotNull { TextNormalizer.groupKey(it) }.toHashSet()
        val overrides = trackDao.allOverrides().associate { it.trackUri to it.primaryArtist }

        val now = System.currentTimeMillis()
        var unreadable = 0
        for (batch in toProcess.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val results = coroutineScope {
                batch.map { file ->
                    async(readDispatcher) { file to metadataReader.read(file.uri) }
                }.awaitAll()
            }

            val upserts = ArrayList<TrackEntity>(batch.size)
            val removals = ArrayList<Long>()
            for ((file, meta) in results) {
                val old = existing[file.uri.toString()]?.takeIf { it.mediaType == MediaKind.AUDIO }
                if (meta == null) {
                    // Unreadable now (possibly only temporarily, e.g. the file is being written).
                    // A file that is already indexed keeps its existing row; a new one is not added.
                    unreadable++
                    continue
                }
                val title = meta.title ?: file.displayName.substringBeforeLast('.')
                val derived = TrackIndexing.derive(
                    title = title,
                    artist = meta.artist,
                    albumArtist = meta.albumArtist,
                    album = meta.album,
                    genre = meta.genre,
                    knownGroupNames = groupNames,
                    primaryOverride = overrides[file.uri.toString()],
                )
                upserts.add(
                    TrackEntity(
                        id = old?.id ?: 0L,
                        uri = file.uri.toString(),
                        mediaType = MediaKind.AUDIO,
                        displayName = file.displayName,
                        mimeType = file.mimeType,
                        sizeBytes = file.size,
                        lastModified = file.lastModified,
                        title = title,
                        artist = meta.artist,
                        albumArtist = meta.albumArtist,
                        album = meta.album,
                        genre = meta.genre,
                        year = meta.year,
                        trackNumber = meta.trackNumber,
                        discNumber = meta.discNumber,
                        durationMs = meta.durationMs,
                        hasArtwork = meta.hasArtwork,
                        isAvailable = true,
                        isExcluded = false,
                        scannedAt = now,
                        primaryArtist = derived.primaryArtist,
                        primaryArtistKey = derived.primaryArtistKey,
                        autoPrimaryArtist = derived.autoPrimaryArtist,
                        albumKey = derived.albumKey,
                        albumGroupKey = derived.albumGroupKey,
                        genreKey = derived.genreKey,
                        titleSort = derived.titleSort,
                        artistSort = derived.artistSort,
                        albumSort = derived.albumSort,
                        dateAdded = file.dateAdded,
                        indexVersion = TrackIndexing.INDEX_VERSION,
                    ),
                )
            }
            writeBatch(upserts, removals)
            val unreadableNow = unreadable
            coordinator.update { it.copy(processed = it.processed + batch.size, unreadable = unreadableNow) }
        }
    }

    private suspend fun writeBatch(upserts: List<TrackEntity>, removals: List<Long>) {
        if (upserts.isNotEmpty()) {
            val newIds = trackDao.upsertAll(upserts)
            searchIndex.upsert(
                upserts.mapIndexed { i, track ->
                    SearchIndex.Row(
                        trackId = if (track.id != 0L) track.id else newIds[i],
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        genre = track.genre,
                        year = track.year,
                        primaryArtist = track.primaryArtist,
                    )
                }.filter { it.trackId > 0 },
            )
        }
        for (chunk in removals.chunked(SQL_CHUNK)) trackDao.deleteByIds(chunk)
        searchIndex.delete(removals)
    }

    /** Lists the supported audio files. Returns null when the query fails (the index is then left alone). */
    private fun queryAudio(): List<FoundMedia>? {
        val collection = MediaStoreAudio.collection()
        val flagColumns = MediaStoreAudio.nonMusicFlagColumns()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
        ) + flagColumns.toTypedArray()

        val cursor = (
            try {
                context.contentResolver.query(collection, projection, null, null, null)
            } catch (e: Exception) {
                null
            }
            ) ?: return null

        val result = ArrayList<FoundMedia>()
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val flagCols = flagColumns.map { c.getColumnIndexOrThrow(it) }

            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: ""
                if (!MediaStoreAudio.isSupportedName(name)) continue
                if (flagCols.any { c.getInt(it) != 0 }) continue
                result.add(
                    FoundMedia(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        displayName = name,
                        mimeType = c.getString(mimeCol),
                        // MediaStore stores seconds.
                        lastModified = c.getLong(modifiedCol) * 1000L,
                        dateAdded = c.getLong(addedCol) * 1000L,
                        size = c.getLong(sizeCol),
                    ),
                )
            }
        }
        return result
    }

    private companion object {
        const val BATCH_SIZE = 60
        const val SQL_CHUNK = 500
    }
}
