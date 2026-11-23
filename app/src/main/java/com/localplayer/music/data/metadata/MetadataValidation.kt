package com.localplayer.music.data.metadata

/** Input validation before anything touches a file. Text is validated, never rewritten. */
object MetadataValidation {

    private val yearPattern = Regex("""\d{4}(-\d{2}(-\d{2})?)?""")
    private val numberPattern = Regex("""\d{1,4}""")

    /** Returns an error message, or null when the value is acceptable for [field]. */
    fun check(field: MetadataField, value: String?): String? {
        if (value == null) return null
        if (value.any { it == '\u0000' }) return "The text contains an invalid character."
        return when (field) {
            MetadataField.YEAR ->
                if (value.isEmpty() || yearPattern.matches(value)) null else "Year must look like 2025 or 2025-03-01."
            MetadataField.TRACK_NUMBER, MetadataField.DISC_NUMBER ->
                if (value.isEmpty() || numberPattern.matches(value)) null else "Use a whole number (for example 3)."
            MetadataField.REPLAYGAIN_TRACK, MetadataField.REPLAYGAIN_ALBUM ->
                if (value.isEmpty() || canonicalGain(value) != null) null else "Use a number in dB (for example -6.50)."
            MetadataField.REPLAYGAIN_TRACK_PEAK, MetadataField.REPLAYGAIN_ALBUM_PEAK ->
                if (value.isEmpty() || canonicalPeak(value) != null) null else "Use a positive number (for example 0.987654)."
            else -> null
        }
    }

    /** "0.9876543" -> "0.987654" (six decimals, as ReplayGain writes it); null when it is not a usable peak. */
    fun canonicalPeak(text: String?): String? {
        val v = com.localplayer.music.data.effects.ReplayGainMath.parsePeak(text?.trim()?.replace(',', '.')) ?: return null
        return String.format(java.util.Locale.US, "%.6f", v.toDouble())
    }

    /** "-6.5", "-6,5 dB" -> "-6.50"; null when it is not a usable gain. Always two decimals, never "-0.00". */
    fun canonicalGain(text: String?): String? {
        val v = com.localplayer.music.data.effects.ReplayGainMath.parseGainDb(text?.trim()?.replace(',', '.')) ?: return null
        val rounded = Math.round(v * 100) / 100.0
        return String.format(java.util.Locale.US, "%.2f", if (rounded == 0.0) 0.0 else rounded)
    }

    /**
     * Diff between what the file has and what the user typed. Only fields that really differ
     * are returned; an emptied field becomes null (= remove it from the file).
     */
    fun changesFrom(original: TrackMetadata, edited: Map<MetadataField, String>): MetadataChanges {
        val changes = LinkedHashMap<MetadataField, String?>()
        for ((field, typed) in edited) {
            val before = original.value(field)
            val after: String? = when {
                typed.isEmpty() -> null
                field in MetadataField.peakFields -> canonicalPeak(typed) ?: typed
                field in MetadataField.gainFields -> canonicalGain(typed) ?: typed
                else -> typed
            }
            if (before != after) changes[field] = after
        }
        return changes
    }
}
