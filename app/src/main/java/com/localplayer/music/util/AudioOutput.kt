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
    /**
     * Name of the device the sound most likely goes to now (Bluetooth, headphones, USB), or null for the phone's own
     * speaker. A best guess from the connected outputs (Android does not say which one is active for media).
     */
    fun currentName(context: Context): String? {
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return null
        val outputs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        // Bluetooth A2DP (8), BLE headset / speaker (26 / 27, Android 12+), hearing aid (23), wired headphones /
        // headset (4 / 3), USB headset / device (22 / 11), HDMI (9): the first one present wins.
        val order = listOf(8, 26, 27, 23, 4, 3, 22, 11, 9)
        for (type in order) {
            outputs.firstOrNull { it.type == type }?.let { d ->
                val name = d.productName?.toString().orEmpty()
                if (name.isNotBlank()) return name
            }
        }
        return null
    }

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
