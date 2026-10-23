package com.localplayer.music.playback

/**
 * The player's "normal" volume (1.0 unless ReplayGain lowers it for the current track). The sleep-timer fade works
 * relative to it, so a fade-out never jumps above the ReplayGain level and a cancelled timer restores it.
 * Only the player's own volume is touched, never the system volume or other apps.
 */
object PlaybackVolume {
    @Volatile var base: Float = 1f
}
