package com.localplayer.music.data.library

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.localplayer.music.data.db.MediaKind
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.db.TrackDao
import com.localplayer.music.data.db.TrackEntity
import com.localplayer.music.util.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FAST scanner (Android 11+): uses ONLY MediaStore.Audio.Media, both to find the files and to get their
 * tags. No audio file is opened, no filesystem walk, no video collection, no fallback to the normal
 * scanner. It is much faster but can be less accurate, because it trusts MediaStore's own copy of the tags
 * (for example MediaStore may file an album-less song under its folder name).
 *
 * The audio file stays the source of truth: rows created here are marked [TrackIndexing.INDEX_VERSION_FAST],
 * and the normal scanner re-reads every such row from its file as soon as the user switches back.
 */
class MediaStoreFastScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val searchIndex: SearchIndex,
    private val coordinator: ScanCoordinator,
) {
    suspend fun scanAll() = coordinator.run { runPass() }

    private suspend fun runPass() {
        coordinator.begin()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!MediaAccess.audioGranted(context)) return

        val found = withContext(Dispatchers.IO) { query() }
        if (found == null) {
            coordinator.fail(ScanFailureKind.QUERY_FAILED)
            return
        }
        coordinator.update { it.copy(discovered = found.size) }

        val existing = trackDao.getSyncInfo().associateBy { it.uri }
        val foundUris = HashSet<String>(found.size * 2)
        for (row in found) foundUris.add(row.uri)

        val deletedIds = existing.values
            .filter { it.uri !in foundUris || it.mediaType != MediaKind.AUDIO }
            .map { it.id }
        for (chunk in deletedIds.chunked(500)) trackDao.deleteByIds(chunk)
        searchIndex.delete(deletedIds)

        // Rows already read accurately from their file are kept as long as the file did not change.
        val changed = found.filter { row ->
            val old = existing[row.uri]
            old == null ||
                old.mediaType != MediaKind.AUDIO ||
                old.lastModified != row.lastModified ||
                old.sizeBytes != row.sizeBytes ||
                old.indexVersion == 0
        }
        coordinator.update { it.copy(toProcess = changed.size) }
        if (changed.isEmpty()) return

        val groupNames = trackDao.distinctAlbumArtists().mapNotNull { TextNormalizer.groupKey(it) }.toHashSet()
        val overrides = trackDao.allOverrides().associate { it.trackUri to it.primaryArtist }
        val now = System.currentTimeMillis()

        for (batch in changed.chunked(300)) {
            val entities = batch.map { row ->
                val old = existing[row.uri]?.takeIf { it.mediaType == MediaKind.AUDIO }
                val title = row.title ?: row.displayName.substringBeforeLast('.')
                val derived = TrackIndexing.derive(
                    title = title,
                    artist = row.artist,
                    albumArtist = row.albumArtist,
                    album = row.album,
                    genre = row.genre,
                    knownGroupNames = groupNames,
                    primaryOverride = overrides[row.uri],
                )
                TrackEntity(
                    id = old?.id ?: 0L,
                    uri = row.uri,
                    mediaType = MediaKind.AUDIO,
                    displayName = row.displayName,
                    mimeType = row.mimeType,
                    sizeBytes = row.sizeBytes,
                    lastModified = row.lastModified,
                    title = title,
                    artist = row.artist,
                    albumArtist = row.albumArtist,
                    album = row.album,
                    genre = row.genre,
                    year = row.year,
                    trackNumber = row.trackNumber,
                    discNumber = row.discNumber,
                    durationMs = row.durationMs,
                    // MediaStore cannot tell. This is "unknown, assume yes": the artwork loader reads the real
                    // file when the art is first needed and, if there is none, clears it (clearUnverifiedArtwork),
                    // so group artwork never stays on a track without a picture.
                    hasArtwork = true,
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
                    dateAdded = row.dateAdded,
                    indexVersion = TrackIndexing.INDEX_VERSION_FAST,
                )
            }
            val newIds = trackDao.upsertAll(entities)
            searchIndex.upsert(
                entities.mapIndexed { i, t ->
                    SearchIndex.Row(
                        trackId = if (t.id != 0L) t.id else newIds[i],
                        title = t.title, artist = t.artist, album = t.album,
                        genre = t.genre, year = t.year, primaryArtist = t.primaryArtist,
                    )
                }.filter { it.trackId > 0 },
            )
            coordinator.update { it.copy(processed = it.processed + batch.size) }
        }
    }

    private class Row(
        val uri: String,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val lastModified: Long,
        val dateAdded: Long,
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val genre: String?,
        val year: Int?,
        val trackNumber: Int?,
        val discNumber: Int?,
        val durationMs: Long,
    )

    /** Returns null when the query fails (the index is then left alone). */
    private fun query(): List<Row>? {
        val collection = MediaStoreAudio.collection()
        val flagColumns = MediaStoreAudio.nonMusicFlagColumns()
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.YEAR,
            MediaStore.Audio.AudioColumns.DURATION,
            // Android 11+ columns (this scanner requires Android 11):
            MediaStore.Audio.AudioColumns.ALBUM_ARTIST,
            MediaStore.Audio.AudioColumns.GENRE,
            MediaStore.MediaColumns.CD_TRACK_NUMBER,
            MediaStore.MediaColumns.DISC_NUMBER,
        ) + flagColumns.toTypedArray()

        val cursor = (
            try {
                context.contentResolver.query(collection, columns, null, null, null)
            } catch (e: Exception) {
                null
            }
            ) ?: return null

        val rows = ArrayList<Row>()
        cursor.use { c ->
            fun col(name: String) = c.getColumnIndexOrThrow(name)
            val id = col(MediaStore.MediaColumns._ID)
            val name = col(MediaStore.MediaColumns.DISPLAY_NAME)
            val mime = col(MediaStore.MediaColumns.MIME_TYPE)
            val size = col(MediaStore.MediaColumns.SIZE)
            val modified = col(MediaStore.MediaColumns.DATE_MODIFIED)
            val added = col(MediaStore.MediaColumns.DATE_ADDED)
            val title = col(MediaStore.MediaColumns.TITLE)
            val artist = col(MediaStore.Audio.AudioColumns.ARTIST)
            val album = col(MediaStore.Audio.AudioColumns.ALBUM)
            val year = col(MediaStore.Audio.AudioColumns.YEAR)
            val duration = col(MediaStore.Audio.AudioColumns.DURATION)
            val albumArtist = col(MediaStore.Audio.AudioColumns.ALBUM_ARTIST)
            val genre = col(MediaStore.Audio.AudioColumns.GENRE)
            val track = col(MediaStore.MediaColumns.CD_TRACK_NUMBER)
            val disc = col(MediaStore.MediaColumns.DISC_NUMBER)
            val flags = flagColumns.map { col(it) }

            while (c.moveToNext()) {
                val displayName = c.getString(name) ?: ""
                if (!MediaStoreAudio.isSupportedName(displayName)) continue
                if (flags.any { c.getInt(it) != 0 }) continue
                rows.add(
                    Row(
                        uri = ContentUris.withAppendedId(collection, c.getLong(id)).toString(),
                        displayName = displayName,
                        mimeType = c.getString(mime),
                        sizeBytes = c.getLong(size),
                        lastModified = c.getLong(modified) * 1000L,
                        dateAdded = c.getLong(added) * 1000L,
                        title = MediaStoreAudio.cleanText(c.getString(title)),
                        artist = MediaStoreAudio.cleanText(c.getString(artist)),
                        albumArtist = MediaStoreAudio.cleanText(c.getString(albumArtist)),
                        album = MediaStoreAudio.cleanText(c.getString(album)),
                        genre = MediaStoreAudio.cleanText(c.getString(genre)),
                        year = c.getInt(year).takeIf { it > 0 },
                        trackNumber = TrackIndexing.parseLeadingInt(c.getString(track)),
                        discNumber = TrackIndexing.parseLeadingInt(c.getString(disc)),
                        durationMs = c.getLong(duration),
                    ),
                )
            }
        }
        return rows
    }
}
