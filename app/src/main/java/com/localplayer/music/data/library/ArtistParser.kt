package com.localplayer.music.data.library

/**
 * Finds the PRIMARY artist of a credit like "Artist A feat. Artist B".
 * The credit itself is never modified; this only decides where the track is filed in the library.
 *
 * Priority:
 * 1. structured multi-value artist tags (first value),
 * 2. unmistakable collaboration markers (feat. / ft. / featuring, ";"),
 * 3. conservative handling of ambiguous separators (" & ", " x ", ", "),
 * 4. the whole credit.
 * If unsure, the credit is NOT split (a missed collaboration is better than a wrong artist).
 */
object ArtistParser {

    /** Well-known acts whose names contain separators. Extended at runtime by album-artist group names. */
    private val protectedNames: Set<String> = setOf(
        "earth, wind & fire", "simon & garfunkel", "hall & oates", "crosby, stills, nash & young",
        "crosby, stills & nash", "emerson, lake & palmer", "tyler, the creator", "mumford & sons",
        "years & years", "hootie & the blowfish", "brooks & dunn", "ike & tina turner",
        "sly & the family stone", "kool & the gang", "bob marley & the wailers", "the mamas & the papas",
        "belle & sebastian", "echo & the bunnymen", "gladys knight & the pips", "iron & wine",
        "florence + the machine", "marina & the diamonds", "bonnie & clyde", "coheed and cambria",
    ).map { TextNormalizer.groupKey(it)!! }.toSet()

    private val featMarker = Regex("""(?i)(?:^|\s)[(\[]?\s*(?:feat|ft|featuring)\b\.?\s""")
    private val weakDelimiters = listOf(", ", " & ", " x ", " X ")
    private val commaSuffixesNotCollaboration = listOf("the ", "jr", "sr", "inc", "llc", "esq", "ii", "iii")

    /**
     * @param artist the ARTIST tag text
     * @param albumArtist the ALBUM ARTIST tag text, used as evidence for group names
     * @param structured separate ARTIST values when the file stores several
     * @param knownGroupNames group keys (see [TextNormalizer.groupKey]) of names known to be single acts
     * @return the primary artist exactly as written in the credit, or null when there is no artist
     */
    fun primaryArtist(
        artist: String?,
        albumArtist: String? = null,
        structured: List<String> = emptyList(),
        knownGroupNames: Set<String> = emptySet(),
    ): String? {
        val values = structured.map { it.trim() }.filter { it.isNotEmpty() }
        if (values.size > 1) return values.first()

        val credit = artist?.trim().orEmpty()
        if (credit.isEmpty()) return null

        // 1. "feat." style markers are unambiguous.
        featMarker.find(credit)?.let { match ->
            val left = credit.substring(0, match.range.first).trim().trimEnd('(', '[', '-', ',', ' ')
            if (left.isNotEmpty()) return left
        }

        // 2. ";" separates credits.
        if (credit.contains(';')) {
            val first = credit.substringBefore(';').trim()
            if (first.isNotEmpty()) return first
        }

        // 3. Ambiguous separators: only split when nothing says it is a single act.
        val creditKey = TextNormalizer.groupKey(credit)
        if (creditKey in protectedNames) return credit
        if (creditKey != null && creditKey in knownGroupNames) return credit
        val albumArtistKey = TextNormalizer.groupKey(albumArtist)
        if (albumArtistKey != null && albumArtistKey == creditKey) return credit

        var bestIndex = -1
        var bestDelimiter = ""
        for (delimiter in weakDelimiters) {
            val index = credit.indexOf(delimiter)
            if (index > 0 && (bestIndex == -1 || index < bestIndex)) {
                bestIndex = index
                bestDelimiter = delimiter
            }
        }
        if (bestIndex == -1) return credit

        val left = credit.substring(0, bestIndex).trim()
        val right = credit.substring(bestIndex + bestDelimiter.length).trim()
        if (left.length < 2 || right.length < 2) return credit
        if (bestDelimiter == ", " && commaSuffixesNotCollaboration.any { right.lowercase().startsWith(it) }) return credit
        if ((bestDelimiter == " x " || bestDelimiter == " X ") && !right.first().isLetterOrDigit()) return credit
        return left
    }
}
