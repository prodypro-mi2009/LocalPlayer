package com.localplayer.music.data.personalization

/**
 * Look of the Shuffle / History / sort row of the library tabs. [PILL]: while the list scrolls, the buttons glide
 * together into a floating pill, in step with the scrolling (the default). [CLASSIC]: the row stays at the top and the
 * list lives below it; on Favorites the row scrolls away with the list.
 */
enum class ActionRowStyle(val key: String) {
    PILL("pill"),
    CLASSIC("classic"),
    ;

    companion object {
        fun fromKey(key: String?): ActionRowStyle = entries.firstOrNull { it.key == key } ?: PILL
    }
}

/** Which side the pill packs to. Independent of the back button setting (left by default). */
enum class ActionRowSide(val key: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromKey(key: String?): ActionRowSide = entries.firstOrNull { it.key == key } ?: LEFT
    }
}
