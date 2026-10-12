package com.localplayer.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen

/**
 * Settings main page: only a list of cards. Each card opens its own sub-screen, so nothing is dumped flat here
 * (Appearance holds the theme, the colour settings and the font settings; Library holds scanning, folders, hidden
 * songs and tab order; and so on).
 */
@Composable
fun SettingsScreen(nav: NavState, @Suppress("UNUSED_PARAMETER") viewModel: LibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.settings), onBack = { nav.pop() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(R.string.settings_appearance, R.string.settings_card_appearance) { nav.push(Screen.SettingsAppearance) }
            SettingsCard(R.string.settings_language, R.string.settings_card_language) { nav.push(Screen.SettingsLanguage) }
            SettingsCard(R.string.settings_library, R.string.settings_card_library) { nav.push(Screen.SettingsLibrary) }
            SettingsCard(R.string.settings_lyrics, R.string.settings_card_lyrics) { nav.push(Screen.SettingsLyrics) }
            SettingsCard(R.string.settings_your_mix, R.string.settings_card_your_mix) { nav.push(Screen.SettingsYourMix) }
            SettingsCard(R.string.stats_title, R.string.settings_card_stats) { nav.push(Screen.TimeUsage) }
            SettingsCard(R.string.settings_about, R.string.settings_card_about) { nav.push(Screen.SettingsAbout) }
            Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
        }
    }
}

/** A rounded card with a title and a one-line description; the whole card is the button. */
@Composable
internal fun SettingsCard(title: Int, subtitle: Int, onClick: () -> Unit) {
    val name = stringResource(title)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = name, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
