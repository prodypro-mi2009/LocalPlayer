package com.localplayer.music.data.library

import java.text.Normalizer
import java.util.Locale

/**
 * Normalization used ONLY for grouping, sorting and searching inside the app.
 * File metadata itself is never rewritten with these.
 */
object TextNormalizer {
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{M}+")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Grouping key: case-insensitive, whitespace-collapsed. Accents are kept, so different names
     * are not merged by accident ("Artist A" == "artist a" == " ARTIST  A ").
     */
    fun groupKey(text: String?): String? {
        if (text == null) return null
        val cleaned = Normalizer.normalize(text, Normalizer.Form.NFC).trim().replace(whitespace, " ")
        if (cleaned.isEmpty()) return null
        return cleaned.lowercase(Locale.ROOT).replace('ς', 'σ')
    }

    /**
     * Search / sort folding: no case, no accents (Greek tonos, dialytika, Latin accents),
     * final sigma = sigma, punctuation becomes a space.
     * "Μουσική" -> "μουσικη", "ΜΟΥΣΙΚΗ" -> "μουσικη", "Beyoncé" -> "beyonce".
     */
    fun fold(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace('ς', 'σ')
            .replace(nonAlphanumeric, " ")
            .trim()
    }

    /** Distinct folded search terms, at most [limit]. */
    fun searchTokens(query: String, limit: Int = 8): List<String> =
        fold(query).split(' ').filter { it.isNotEmpty() }.distinct().take(limit)
}
