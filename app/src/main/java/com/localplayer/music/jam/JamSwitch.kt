package com.localplayer.music.jam

/**
 * Master switch of the Jam feature (listening together over the local network).
 *
 * `false` = Jam is NOT part of the app: no Settings entry, no screen, no service, no receiver, no network server.
 * Because this is a compile-time constant, the release build (R8) removes all the Jam code from the APK, while the
 * source files stay in the project untouched.
 *
 * To bring Jam back, set this to `true` and re-enable the three commented-out blocks in AndroidManifest.xml
 * (marked "JAM DISABLED"): the ACCESS_LOCAL_NETWORK permission, [JamVoteReceiver] and [JamService].
 */
object JamSwitch {
    const val ENABLED = false
}
