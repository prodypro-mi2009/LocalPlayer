package com.localplayer.music

import android.app.Application
import com.localplayer.music.data.metadata.FileTagEngine

class LocalPlayerApp : Application() {
    /** Manual dependency container. Everything inside is created lazily. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Tag library setup (Android image handling, exact-text options). Cheap and does no file I/O.
        FileTagEngine.configure(android = true)
    }
}
