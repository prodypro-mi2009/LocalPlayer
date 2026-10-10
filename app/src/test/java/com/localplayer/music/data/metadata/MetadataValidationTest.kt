package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataValidationTest {
    private val original = TrackMetadata(
        container = AudioContainer.MP3, title = "Song", artist = "Rack feat. X", year = "2025", trackNumber = "3",
    )

    @Test fun onlyDifferentFieldsAreChanges() {
        val changes = MetadataValidation.changesFrom(
            original,
            mapOf(MetadataField.TITLE to "Song", MetadataField.ARTIST to "Rack", MetadataField.YEAR to "2025"),
        )
        assertEquals(mapOf<MetadataField, String?>(MetadataField.ARTIST to "Rack"), changes)
    }
    @Test fun emptyTextMeansRemoveFieldOnlyWhenItExisted() {
        val changes = MetadataValidation.changesFrom(original, mapOf(MetadataField.TITLE to "", MetadataField.ALBUM to ""))
        assertEquals(mapOf<MetadataField, String?>(MetadataField.TITLE to null), changes)
    }
    @Test fun textIsNotTrimmedOrRewritten() {
        val changes = MetadataValidation.changesFrom(original, mapOf(MetadataField.ARTIST to " Rack, X "))
        assertEquals(" Rack, X ", changes[MetadataField.ARTIST])
    }
    @Test fun yearValidation() {
        assertNull(MetadataValidation.check(MetadataField.YEAR, "2026"))
        assertNull(MetadataValidation.check(MetadataField.YEAR, "2026-03-01"))
        assertNull(MetadataValidation.check(MetadataField.YEAR, ""))
        assertNotNull(MetadataValidation.check(MetadataField.YEAR, "20x6"))
    }
    @Test fun numberValidation() {
        assertNull(MetadataValidation.check(MetadataField.TRACK_NUMBER, "12"))
        assertNotNull(MetadataValidation.check(MetadataField.TRACK_NUMBER, "3/12"))
        assertNotNull(MetadataValidation.check(MetadataField.DISC_NUMBER, "a"))
    }
    @Test fun nulCharacterRejected() = assertNotNull(MetadataValidation.check(MetadataField.TITLE, "a\u0000b"))
    @Test fun anyOtherTextAccepted() = assertNull(MetadataValidation.check(MetadataField.ARTIST, "Rack, X & Y; feat. Z (Ελληνικά)"))
}
