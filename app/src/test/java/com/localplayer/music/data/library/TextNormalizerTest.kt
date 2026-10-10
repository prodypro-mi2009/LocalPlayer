package com.localplayer.music.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {
    @Test fun greekCaseAndAccents() {
        assertEquals("μουσικη", TextNormalizer.fold("Μουσική"))
        assertEquals("μουσικη", TextNormalizer.fold("μουσικη"))
        assertEquals("μουσικη", TextNormalizer.fold("ΜΟΥΣΙΚΗ"))
    }
    @Test fun finalSigma() = assertEquals(TextNormalizer.fold("ΟΔΥΣΣΕΥΣ"), TextNormalizer.fold("οδυσσευσ"))
    @Test fun latinAccents() = assertEquals("beyonce", TextNormalizer.fold("Beyoncé"))
    @Test fun punctuationBecomesSpace() = assertEquals("rack feat x", TextNormalizer.fold("Rack feat. X"))
    @Test fun groupKeyIgnoresCaseAndWhitespace() {
        assertEquals(TextNormalizer.groupKey("Artist A"), TextNormalizer.groupKey("artist a"))
        assertEquals(TextNormalizer.groupKey("Artist A"), TextNormalizer.groupKey("  ARTIST   A "))
    }
    @Test fun groupKeyKeepsAccentsDistinct() {
        assert(TextNormalizer.groupKey("Rosé") != TextNormalizer.groupKey("Rose"))
    }
    @Test fun groupKeyBlankIsNull() = assertNull(TextNormalizer.groupKey("   "))
    @Test fun tokens() = assertEquals(listOf("μουσικη", "rack"), TextNormalizer.searchTokens("Μουσική, RACK!"))
}
