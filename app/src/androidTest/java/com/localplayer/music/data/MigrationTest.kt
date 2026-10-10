package com.localplayer.music.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localplayer.music.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real 3 -> 4 migration on a database created with the version 3 schema
 * (schemas/.../3.json) and lets Room validate the result against the version 4 entities.
 * Requires a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val dbName = "migration-test"

    @Test
    fun migrate4To5_addsFavorites_andKeepsEverythingElse() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (1, 'content://media/external/audio/media/1', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack', NULL, 'Album', 'Pop', 2025, 1, 1, 180000, 0, 1, 0, 9)",
            )
            execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Mix', 1, 1)")
            execSQL("INSERT INTO playlist_tracks (playlistId, trackUri, position, addedAt) VALUES (1, 'content://media/external/audio/media/1', 0, 1)")
            execSQL("INSERT INTO listening_history (trackUri, playedAt) VALUES ('content://media/external/audio/media/1', 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5)
        db.query("SELECT COUNT(*) FROM tracks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM playlist_tracks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM listening_history").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO favorites (trackUri, addedAt) VALUES ('content://media/external/audio/media/1', 7)")
        db.query("SELECT COUNT(*) FROM favorites").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun migrate3To4_keepsExistingTracks_andAddsTheNewTables() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (7, 'content://media/external/audio/media/7', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack feat. X', NULL, 'Album X', 'Hip-Hop', 2025, 3, 1, " +
                    "180000, 1, 1, 0, 9)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)

        db.query("SELECT id, uri, title, artist, album, year, indexVersion, dateAdded, primaryArtistKey FROM tracks").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
            assertEquals("content://media/external/audio/media/7", c.getString(1))
            assertEquals("Song", c.getString(2))
            assertEquals("Rack feat. X", c.getString(3))
            assertEquals("Album X", c.getString(4))
            assertEquals(2025, c.getInt(5))
            // Rows are flagged for a one-time re-read of the file so the new columns get filled.
            assertEquals(0, c.getInt(6))
            assertEquals(0L, c.getLong(7))
            assertEquals(true, c.isNull(8))
        }

        // New tables work, and the playlist foreign key is enforced.
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'P', 1, 1)")
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackUri, position, addedAt) VALUES (1, 'u', 0, 1)")
        db.execSQL("INSERT INTO listening_history (trackUri, playedAt) VALUES ('u', 1)")
        db.execSQL("INSERT INTO primary_artist_overrides (trackUri, primaryArtist) VALUES ('u', 'Rack')")
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun migrate5To6_keepsEverything_andAddsFolderAndHiddenTables() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO tracks (id, uri, mediaType, displayName, mimeType, sizeBytes, lastModified, title, artist, " +
                    "albumArtist, album, genre, year, trackNumber, discNumber, durationMs, hasArtwork, isAvailable, " +
                    "isExcluded, scannedAt) VALUES (3, 'content://media/external/audio/media/3', 0, 'a.mp3', " +
                    "'audio/mpeg', 1000, 5000, 'Song', 'Rack', NULL, 'Album', 'Pop', 2025, 1, 1, 180000, 0, 1, 0, 9)",
            )
            execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'P', 1, 1)")
            execSQL("INSERT INTO favorites (trackUri, addedAt) VALUES ('content://media/external/audio/media/3', 2)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)

        db.query("SELECT id, title, folderKey FROM tracks").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(3L, c.getLong(0))
            assertEquals("Song", c.getString(1))
            // Filled in by the next scan, without re-reading the file.
            assertEquals(true, c.isNull(2))
        }
        db.query("SELECT name, sourceFolderKey, sourceIncludesSubfolders, folderSyncStatus FROM playlists").use { c ->
            c.moveToFirst()
            assertEquals("P", c.getString(0))
            assertEquals(true, c.isNull(1))
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM favorites").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.execSQL("INSERT INTO hidden_tracks (trackUri, hiddenAt, displayName, sizeBytes) VALUES ('u', 1, 'a.mp3', 10)")
        db.execSQL("INSERT INTO excluded_folders (prefix, excludedAt) VALUES ('external_primary:Podcasts/', 1)")
        db.execSQL("INSERT INTO library_folders (treeUri, folderKey, label, addedAt) VALUES ('t', 'k', 'L', 1)")
    }
}
