package com.localplayer.music.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.db.AppDatabase
import com.localplayer.music.data.db.HistoryEntity
import com.localplayer.music.data.db.SearchIndex
import com.localplayer.music.data.db.TrackEntity
import com.localplayer.music.data.library.LibraryRepository
import com.localplayer.music.data.library.LibraryScanner
import com.localplayer.music.data.library.MediaStoreFastScanner
import com.localplayer.music.data.library.MetadataReader
import com.localplayer.music.data.library.ScanCoordinator
import com.localplayer.music.data.library.TrackIndexing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Playlists, history, classification and search on a real (in-memory) Room database. Needs a device or emulator. */
@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var searchIndex: SearchIndex
    private lateinit var repo: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        SearchIndex.ensureTable(db.openHelper.writableDatabase)
        searchIndex = SearchIndex(db)
        val coordinator = ScanCoordinator()
        repo = LibraryRepository(
            database = db,
            scanner = LibraryScanner(context, db.trackDao(), MetadataReader(context), searchIndex, coordinator),
            fastScanner = MediaStoreFastScanner(context, db.trackDao(), searchIndex, coordinator),
            coordinator = coordinator,
            settings = AppSettings(context),
            searchIndex = searchIndex,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addTrack(n: Int, title: String, artist: String?, album: String? = "Album", genre: String? = "Pop", year: Int? = 2020): String {
        val uri = "content://media/external/audio/media/$n"
        val derived = TrackIndexing.derive(title, artist, null, album, genre, emptySet(), null)
        val ids = db.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    uri = uri, mediaType = 0, displayName = "$n.mp3", mimeType = "audio/mpeg", sizeBytes = 1, lastModified = 1,
                    title = title, artist = artist, albumArtist = null, album = album, genre = genre, year = year,
                    trackNumber = n, discNumber = 1, durationMs = 1000L * n, hasArtwork = false, scannedAt = 1,
                    primaryArtist = derived.primaryArtist, primaryArtistKey = derived.primaryArtistKey,
                    autoPrimaryArtist = derived.autoPrimaryArtist, albumKey = derived.albumKey,
                    albumGroupKey = derived.albumGroupKey, genreKey = derived.genreKey, titleSort = derived.titleSort,
                    artistSort = derived.artistSort, albumSort = derived.albumSort, indexVersion = TrackIndexing.INDEX_VERSION,
                ),
            ),
        )
        searchIndex.upsert(listOf(SearchIndex.Row(ids[0], title, artist, album, genre, year, derived.primaryArtist)))
        return uri
    }

    @Test fun playlistLifecycle() = runBlocking {
        val a = addTrack(1, "One", "Artist A")
        val b = addTrack(2, "Two", "Artist A")
        val c = addTrack(3, "Three", "Artist B")
        val id = repo.createPlaylist("Mix")
        assertEquals(3, repo.addToPlaylist(id, listOf(a, b, c)).added)

        val again = repo.addToPlaylist(id, listOf(a))
        assertEquals(0, again.added)
        assertEquals(1, again.alreadyThere)
        assertEquals(listOf("One", "Two", "Three"), repo.playlistTracks(id).first().map { it.title })

        repo.movePlaylistTrack(id, c, a)
        assertEquals(listOf("Three", "One", "Two"), repo.playlistTracks(id).first().map { it.title })

        repo.removeFromPlaylist(id, b)
        assertEquals(listOf("Three", "One"), repo.playlistTracks(id).first().map { it.title })

        repo.renamePlaylist(id, "Renamed")
        assertEquals("Renamed", repo.playlists().first().single().name)

        repo.deletePlaylist(id)
        assertTrue(repo.playlists().first().isEmpty())
    }

    @Test fun favoritesSurviveReindexingAndTagEdits() = runBlocking {
        val uri = addTrack(1, "Song", "Old Artist")
        assertEquals(true, repo.toggleFavorite(uri))
        assertEquals(true, repo.isFavorite(uri).first())
        // The cache row is rebuilt from the file (what a rescan or a verified tag write does): same URI, new tags.
        val entity = db.trackDao().getEntityByUri(uri)!!
        db.trackDao().upsert(entity.copy(title = "Renamed", artist = "New Artist"))
        assertEquals(true, repo.isFavorite(uri).first())
        assertEquals(listOf("Renamed"), repo.favoriteTracks().first().map { it.title })
        assertEquals(false, repo.toggleFavorite(uri))
        assertTrue(repo.favoriteTracks().first().isEmpty())
    }

    @Test fun trackCanBeInSeveralPlaylists() = runBlocking {
        val a = addTrack(1, "One", "Artist A")
        val p1 = repo.createPlaylist("P1")
        val p2 = repo.createPlaylist("P2")
        repo.addToPlaylist(p1, listOf(a))
        repo.addToPlaylist(p2, listOf(a))
        assertEquals(1, repo.playlistTracks(p1).first().size)
        assertEquals(1, repo.playlistTracks(p2).first().size)
    }

    @Test fun historyIsDeduplicatedNewestFirstAndClearable() = runBlocking {
        val a = addTrack(1, "One", "Artist A")
        val b = addTrack(2, "Two", "Artist A")
        val history = db.historyDao()
        history.insert(HistoryEntity(trackUri = a, playedAt = 100))
        history.insert(HistoryEntity(trackUri = b, playedAt = 200))
        history.insert(HistoryEntity(trackUri = a, playedAt = 300))
        assertEquals(listOf("One", "Two"), repo.recentlyPlayed().first().map { it.title })
        repo.clearHistory()
        assertTrue(repo.recentlyPlayed().first().isEmpty())
    }

    @Test fun artistsUsePrimaryArtistAndOverrideOnlyChangesTheIndex() = runBlocking {
        val a = addTrack(1, "One", "Artist A feat. Artist B")
        addTrack(2, "Two", "artist a")
        assertEquals(listOf("Artist A"), repo.artists().first().map { it.name })
        assertEquals(2, repo.artists().first().single().trackCount)

        repo.setPrimaryArtist(a, "Artist B")
        assertEquals(setOf("Artist A", "Artist B"), repo.artists().first().map { it.name }.toSet())
        // The cached file artist credit is untouched.
        assertEquals("Artist A feat. Artist B", db.trackDao().getEntityByUri(a)!!.artist)

        repo.setPrimaryArtist(a, null)
        assertEquals(listOf("Artist A"), repo.artists().first().map { it.name })
    }

    @Test fun greekSearchIgnoresCaseAndAccents() = runBlocking {
        addTrack(1, "Μουσική", "Ελευθερία Αρβανιτάκη", album = "Άλμπουμ", genre = "Λαϊκά")
        addTrack(2, "Other", "Someone")
        assertEquals(listOf("Μουσική"), repo.search("μουσικη").songs.map { it.title })
        assertEquals(listOf("Μουσική"), repo.search("ΜΟΥΣΙΚΗ").songs.map { it.title })
        assertEquals(1, repo.search("αρβανιτακη").artists.size)
        assertEquals(1, repo.search("αλμπουμ").albums.size)
    }

    @Test fun searchFollowsTheCache_afterAnEditTheOldNameIsGone() = runBlocking {
        val uri = addTrack(1, "Song", "Old Artist")
        assertEquals(1, repo.search("old").songs.size)
        // What a verified file write does: the cache row is rebuilt from the file.
        val entity = db.trackDao().getEntityByUri(uri)!!
        val derived = TrackIndexing.derive("Song", "New Artist", null, "Album", "Pop", emptySet(), null)
        db.trackDao().upsert(
            entity.copy(
                artist = "New Artist", primaryArtist = derived.primaryArtist,
                primaryArtistKey = derived.primaryArtistKey, autoPrimaryArtist = derived.autoPrimaryArtist,
                artistSort = derived.artistSort,
            ),
        )
        searchIndex.upsert(listOf(SearchIndex.Row(entity.id, "Song", "New Artist", "Album", "Pop", 2020, "New Artist")))
        assertEquals(0, repo.search("old").songs.size)
        assertEquals(1, repo.search("new").songs.size)
    }
}
