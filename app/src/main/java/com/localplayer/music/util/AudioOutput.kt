package com.localplayer.music.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Opens the system's own place to pick where the sound goes (phone speaker, Bluetooth headphones, ...). The app never
 * scans for devices itself. What is shown depends on the phone and Android version: the volume panel where it exists,
 * otherwise Bluetooth settings, otherwise the sound settings.
 */
object AudioOutput {
    fun openPicker(context: Context) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Intent(Settings.Panel.ACTION_VOLUME))
            add(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            add(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        for (intent in candidates) {
            val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return
        }
    }
}
