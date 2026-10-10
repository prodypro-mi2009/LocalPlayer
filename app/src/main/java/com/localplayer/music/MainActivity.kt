package com.localplayer.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localplayer.music.playback.SleepTimer
import com.localplayer.music.ui.AppRoot
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.theme.ConfigureSystemBars
import com.localplayer.music.ui.theme.LocalPlayerTheme
import com.localplayer.music.ui.theme.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as LocalPlayerApp).container

    private val viewModel: LibraryViewModel by viewModels {
        viewModelFactory { initializer { LibraryViewModel(container) } }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        // Interface language chosen in Settings (English / Greek); changing it recreates the activity.
        super.attachBaseContext(com.localplayer.music.data.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The sleep timer has stopped playback and the service: close the app too (also while in the background).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                SleepTimer.finished.collect { finishAndRemoveTask() }
            }
        }

        setContent {
            val settings = container.settings
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
            // Window bars follow the APP theme (transparent bars, matching icon colour and window background).
            ConfigureSystemBars(this@MainActivity, themeMode.isDark(isSystemInDarkTheme()))
            LocalPlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.playerConnection.connect()
    }

    override fun onStop() {
        // Playback continues in PlaybackService; we only drop the UI's controller.
        container.playerConnection.release()
        super.onStop()
    }
}
