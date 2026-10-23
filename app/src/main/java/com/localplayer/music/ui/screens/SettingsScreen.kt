package com.localplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.AppTopBar
import com.localplayer.music.ui.components.TopBarMetrics
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
 * Settings main page: ONE grouped container. Every entry is a row with a coloured round icon, a bold title and a
 * one-line description; rows are separated by thin gaps, the outer corners are large and the inner ones small.
 * Each entry opens its own sub-screen, so nothing is dumped flat here.
 */
@Composable
fun SettingsScreen(nav: NavState, @Suppress("UNUSED_PARAMETER") viewModel: LibraryViewModel) {
    val entries = listOf(
        SettingsEntry(R.string.settings_appearance, R.string.settings_card_appearance, AppIcons.Palette, Color(0xFF8A4F6B)) { nav.push(Screen.SettingsAppearance) },
        SettingsEntry(R.string.settings_language, R.string.settings_card_language, AppIcons.Language, Color(0xFF1E5E8C)) { nav.push(Screen.SettingsLanguage) },
        SettingsEntry(R.string.settings_library, R.string.settings_card_library, AppIcons.Folder, Color(0xFF0B5C80)) { nav.push(Screen.SettingsLibrary) },
        SettingsEntry(R.string.settings_navigation, R.string.settings_card_navigation, AppIcons.Compass, Color(0xFF6A4FA0)) { nav.push(Screen.SettingsNavigation) },
        SettingsEntry(R.string.settings_lyrics, R.string.settings_card_lyrics, AppIcons.Lyrics, Color(0xFF5E4B7A)) { nav.push(Screen.SettingsLyrics) },
        SettingsEntry(R.string.settings_your_mix, R.string.settings_card_your_mix, AppIcons.MusicNote, Color(0xFF7A3E5A)) { nav.push(Screen.SettingsYourMix) },
        SettingsEntry(R.string.stats_title, R.string.settings_card_stats, AppIcons.History, Color(0xFF3F5A82)) { nav.push(Screen.TimeUsage) },
        SettingsEntry(R.string.settings_audio, R.string.settings_card_audio, AppIcons.GraphicEq, Color(0xFF7A5A12)) { nav.push(Screen.SettingsAudio) },
        SettingsEntry(R.string.settings_widgets, R.string.settings_card_widgets, AppIcons.Widgets, Color(0xFF2F6B4A)) { nav.push(Screen.SettingsWidgets) },
        SettingsEntry(R.string.dl_title, R.string.settings_card_download, AppIcons.Download, Color(0xFFB23A3A)) { nav.push(Screen.SettingsDownload) },
        SettingsEntry(R.string.settings_backup, R.string.settings_card_backup, AppIcons.CloudUpload, Color(0xFF3A4A80)) { nav.push(Screen.SettingsBackup) },
        SettingsEntry(R.string.settings_about, R.string.settings_card_about, AppIcons.Info, Color(0xFF4A5560)) { nav.push(Screen.SettingsAbout) },
    )
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            leading = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)) {
                    IconButton(onClick = { nav.pop() }, modifier = Modifier.size(TopBarMetrics.ButtonSize)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { i, e -> SettingsRow(e, position = i, count = entries.size) }
            Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
        }
    }
}

internal class SettingsEntry(
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

/** One row of the grouped list: big rounded outer corners on the first/last row, small ones between rows. */
@Composable
internal fun SettingsRow(entry: SettingsEntry, position: Int, count: Int) {
    val name = stringResource(entry.title)
    val outer = 28.dp
    val inner = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (position == 0) outer else inner,
        topEnd = if (position == 0) outer else inner,
        bottomStart = if (position == count - 1) outer else inner,
        bottomEnd = if (position == count - 1) outer else inner,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = name, onClick = entry.onClick)
            .heightIn(min = 88.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(entry.tint), contentAlignment = Alignment.Center) {
            Icon(entry.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(entry.subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A settings hub: a page of coloured cards (same look as the main Settings list) that open the sub-pages. */
@Composable
internal fun SettingsHub(title: Int, nav: NavState, entries: List<SettingsEntry>) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(title), onBack = { nav.pop() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { i, e -> SettingsRow(e, position = i, count = entries.size) }
            Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
        }
    }
}

/** Navigation & gestures: the tab order and the song-row gestures. */
@Composable
fun NavigationHubScreen(nav: NavState) = SettingsHub(
    R.string.settings_navigation, nav,
    listOf(
        SettingsEntry(R.string.settings_tab_order, R.string.settings_card_tab_order, AppIcons.Tab, Color(0xFF4A5FA8)) { nav.push(Screen.SettingsTabOrder) },
        SettingsEntry(R.string.settings_gestures, R.string.settings_card_gestures, AppIcons.Gesture, Color(0xFF8A4A9A)) { nav.push(Screen.SettingsGestures) },
    ),
)
