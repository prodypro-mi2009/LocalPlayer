package com.localplayer.music.ui.components

import com.localplayer.music.data.library.TextNormalizer

/**
 * The genres that have their own picture on the Genres tab (see [GenreIcons]). [of] reads a free-text genre tag
 * ("Hip-Hop/Rap", "Ελληνικό Έντεχνο", "R&B") and picks the closest one, or null when none fits (the card then shows the
 * generic music note). Case, accents and punctuation do not matter. The order of the checks is on purpose: the more
 * specific genre is tested before the general one ("trap" before "rap", "metal" before "rock", "trip hop" is not hip hop).
 */
enum class GenreKind {
    Rock,
    AlternativeRock,
    Pop,
    HipHop,
    Trap,
    Rap,
    Laika,
    Entechno,
    Electronic,
    FunkDisco,
    Jazz,
    RnbSoul,
    Metal,
    Classical,
    ;

    companion object {
        fun of(name: String?): GenreKind? {
            val n = TextNormalizer.fold(name)
            if (n.isEmpty()) return null
            val words = n.split(' ')
            fun has(vararg parts: String) = parts.any { n.contains(it) }
            fun wordStarts(vararg parts: String) = words.any { w -> parts.any { w.startsWith(it) } }
            return when {
                has("metal") -> Metal
                has("alternative", "indie", "grunge", "shoegaze", "alt rock", "post rock", "post punk") -> AlternativeRock
                has("rock", "punk") -> Rock
                has("trap", "drill") -> Trap
                (has("hip hop", "hiphop") && !has("trip hop")) -> HipHop
                wordStarts("rap", "ραπ") -> Rap
                has("greek folk", "greek popular") || wordStarts("laik", "λαικ", "rebet", "ρεμπετ", "skylad", "σκυλαδ") -> Laika
                has("entech", "εντεχν", "acoustic", "ακουστικ", "singer", "unplugged", "folk") -> Entechno
                has(
                    "electro", "edm", "dance", "house", "techno", "trance", "dubstep", "ambient", "synth",
                    "drum and bass", "dnb", "idm", "downtempo", "trip hop", "ηλεκτρον",
                ) -> Electronic
                has("funk", "disco") -> FunkDisco
                has("rhythm and blues", "r and b", "motown") || n == "rnb" || n == "r b" || words.contains("soul") || words.contains("rnb") ||
                    " $n ".contains(" r b ") -> RnbSoul
                has("jazz", "swing", "bebop", "blues", "τζαζ") -> Jazz
                has("classic", "orchestra", "opera", "baroque", "symphon", "concerto", "chamber", "κλασικ", "οπερα") -> Classical
                has("pop") -> Pop
                else -> null
            }
        }
    }
}
