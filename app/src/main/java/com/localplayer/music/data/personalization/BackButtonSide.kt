package com.localplayer.music.data.personalization

/**
 * One-handed use: where the back button is. [OFF] keeps it in the top bar of every screen (the default); [RIGHT] / [LEFT]
 * move it to a round button above the mini player, at the side of the thumb of a right- / left-handed user.
 */
enum class BackButtonSide(val key: String) {
    OFF("off"),
    RIGHT("right"),
    LEFT("left"),
    ;

    companion object {
        fun fromKey(key: String?): BackButtonSide = entries.firstOrNull { it.key == key } ?: OFF
    }
}
