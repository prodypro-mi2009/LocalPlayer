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
            else -> null
        }
    }

    /**
     * Diff between what the file has and what the user typed. Only fields that really differ
     * are returned; an emptied field becomes null (= remove it from the file).
     */
    fun changesFrom(original: TrackMetadata, edited: Map<MetadataField, String>): MetadataChanges {
        val changes = LinkedHashMap<MetadataField, String?>()
        for ((field, typed) in edited) {
            val before = original.value(field)
            val after: String? = if (typed.isEmpty()) null else typed
            if (before != after) changes[field] = after
        }
        return changes
    }
}
