package com.localplayer.music.ui.screens

import com.localplayer.music.ui.components.LocalBottomBarInset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import com.localplayer.music.ui.components.TabOrder
import com.localplayer.music.data.AppLanguage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.R
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.ThemeMode
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import androidx.compose.foundation.clickable

@Composable
fun SettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val settings = viewModel.settings
    val fastScanner by settings.useMediaStoreScanner.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val artworkColors by settings.artworkColors.collectAsStateWithLifecycle()
    val backgroundVersion by settings.backgroundVersion.collectAsStateWithLifecycle()
    val backgroundDim by settings.backgroundDim.collectAsStateWithLifecycle()
    val language by settings.language.collectAsStateWithLifecycle()
    val tabOrderRaw by settings.tabOrder.collectAsStateWithLifecycle()
    val tabOrder = remember(tabOrderRaw) { TabOrder.parse(tabOrderRaw) }
    val context = LocalContext.current
    val container = (context.applicationContext as LocalPlayerApp).container
    val version = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    val background by produceState<ImageBitmap?>(initialValue = null, backgroundVersion) {
        value = if (backgroundVersion == 0L) null else container.background.load()
    }
    // Photo picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setBackground(uri)
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(R.string.settings), onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {

            SectionHeader(stringResource(R.string.settings_library))
            Column(Modifier.padding(horizontal = 16.dp)) {
                songs?.let {
                    Text(stringResource(R.string.songs_in_library, it.size), style = MaterialTheme.typography.bodyMedium)
                }
                if (scan.isScanning) ScanBanner(scan)
                if (!scan.isScanning && scan.error != null) ScanErrorBanner(scan, onRetry = viewModel::rescan)
                if (!scan.isScanning && scan.unreadable > 0) {
                    Text(
                        stringResource(R.string.scan_unreadable, scan.unreadable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = viewModel::rescan,
                    enabled = !scan.isScanning,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(R.string.settings_rescan)) }
                Text(
                    stringResource(R.string.settings_rescan_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            val hiddenCount by viewModel.hiddenCount.collectAsStateWithLifecycle()
            NavRow(
                title = stringResource(R.string.library_folders),
                subtitle = stringResource(R.string.library_folders_summary),
                onClick = { nav.push(Screen.LibraryFolders) },
            )
            NavRow(
                title = stringResource(R.string.hidden_songs),
                subtitle = stringResource(R.string.hidden_songs_count, hiddenCount),
                onClick = { nav.push(Screen.HiddenSongs) },
            )

            // Scanner choice: OFF = normal scanner, ON = MediaStore-only fast scanner (Android 11+).
            val supported = AppSettings.mediaStoreScannerSupported()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_scanner_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_scanner_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!supported) {
                        Text(
                            stringResource(R.string.settings_scanner_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Switch(
                    checked = fastScanner,
                    onCheckedChange = viewModel::setUseMediaStoreScanner,
                    enabled = supported && !scan.isScanning,
                )
            }

            SectionHeader(stringResource(R.string.settings_language))
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppLanguage.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = language == option,
                            onClick = {
                                if (language != option) {
                                    viewModel.setLanguage(option)
                                    // Resources are resolved per activity context: recreate to apply immediately.
                                    (context as? android.app.Activity)?.recreate()
                                }
                            },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == option, onClick = null)
                        // Each language is always shown in its own language.
                        Text(
                            if (option == AppLanguage.ENGLISH) "English" else "Ελληνικά",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_tab_order))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.settings_tab_order_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tabOrder.forEachIndexed { index, tab ->
                    val name = stringResource(tab.label)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(
                            onClick = { viewModel.setTabOrder(TabOrder.move(tabOrder, index, -1)) },
                            enabled = index > 0,
                        ) { Icon(AppIcons.ArrowUp, contentDescription = stringResource(R.string.move_tab_up, name)) }
                        IconButton(
                            onClick = { viewModel.setTabOrder(TabOrder.move(tabOrder, index, +1)) },
                            enabled = index < tabOrder.lastIndex,
                        ) { Icon(AppIcons.ArrowDown, contentDescription = stringResource(R.string.move_tab_down, name)) }
                    }
                }
                TextButton(onClick = viewModel::resetTabOrder, enabled = tabOrderRaw != null) {
                    Text(stringResource(R.string.settings_tab_order_reset))
                }
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = null)
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
                SwitchRow(stringResource(R.string.settings_dynamic_color), dynamicColor, viewModel::setDynamicColor)
                SwitchRow(stringResource(R.string.settings_artwork_colors), artworkColors, viewModel::setArtworkColors)
            }

            // Background picture: preview with a + button at the top-right.
            Text(
                stringResource(R.string.settings_background),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(160.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    val image = background
                    if (image != null) {
                        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        // Same veil the app draws behind its screens, so the preview matches the result.
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = backgroundDim)))
                    } else {
                        Text(
                            stringResource(R.string.background_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    IconButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(AppIcons.Add, contentDescription = stringResource(R.string.background_add))
                    }
                }
            }
            val dimLabel = stringResource(R.string.background_dim)
            val dimPercent = (backgroundDim * 100).toInt()
            Text(
                "$dimLabel · $dimPercent%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Slider(
                value = backgroundDim,
                onValueChange = viewModel::setBackgroundDim,
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = dimLabel },
            )
            if (backgroundVersion != 0L) {
                TextButton(onClick = viewModel::resetBackground, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.background_reset))
                }
            }

            SectionHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.version_label, version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp + LocalBottomBarInset.current),
            )
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
