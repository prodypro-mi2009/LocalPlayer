package com.localplayer.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.R
import com.localplayer.music.data.AppLanguage
import com.localplayer.music.data.AppSettings
import com.localplayer.music.data.ThemeMode
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.localplayer.music.data.mix.MixSettings
import com.localplayer.music.data.mix.MixStrategy
import kotlin.math.roundToInt
import com.localplayer.music.data.personalization.FontCategory
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.components.TabOrder
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.AppFonts

/** Shared frame of every settings sub-screen: back arrow + title + scrolling content with room for the bottom bars. */
@Composable
internal fun SettingsPage(title: Int, nav: NavState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(title), onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(24.dp + LocalBottomBarInset.current))
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
private fun RadioLine(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

// ------------------------------------------------------------------ Appearance

/** Appearance hub: theme, colours and fonts (each is its own page). */
@Composable
fun AppearanceSettingsScreen(nav: NavState, @Suppress("UNUSED_PARAMETER") viewModel: LibraryViewModel) = SettingsHub(
    R.string.settings_appearance, nav,
    listOf(
        SettingsEntry(R.string.settings_theme, R.string.settings_card_theme, AppIcons.Brightness, Color(0xFFB0722A)) { nav.push(Screen.SettingsTheme) },
        SettingsEntry(R.string.settings_colors, R.string.settings_colors_summary, AppIcons.Palette, Color(0xFF8A4F6B)) { nav.push(Screen.Colors) },
        SettingsEntry(R.string.settings_fonts, R.string.settings_fonts_summary, AppIcons.TextFields, Color(0xFF3F7A7A)) { nav.push(Screen.Fonts) },
    ),
)

/** Appearance > Theme: light, dark or follow the system. */
@Composable
fun ThemeSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val pure by viewModel.settings.pureMode.collectAsStateWithLifecycle()
    SettingsPage(R.string.settings_theme, nav) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            ThemeMode.entries.forEach { mode ->
                RadioLine(
                    label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    ),
                    selected = themeMode == mode,
                    enabled = !pure.isOn,
                ) { viewModel.setThemeMode(mode) }
            }
            if (pure.isOn) {
                Text(
                    stringResource(R.string.theme_forced_by_pure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Language

@Composable
fun LanguageSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val language by viewModel.settings.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsPage(R.string.settings_language, nav) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AppLanguage.entries.forEach { option ->
                // Each language is always shown in its own language.
                RadioLine(if (option == AppLanguage.ENGLISH) "English" else "Ελληνικά", language == option) {
                    if (language != option) {
                        viewModel.setLanguage(option)
                        // Resources are resolved per activity context: recreate to apply immediately.
                        (context as? android.app.Activity)?.recreate()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Library

@Composable
fun LibrarySettingsScreen(nav: NavState, viewModel: LibraryViewModel) = SettingsHub(
    R.string.settings_library, nav,
    listOf(
        SettingsEntry(R.string.settings_scan, R.string.settings_card_scan, AppIcons.Refresh, Color(0xFF0B5C80)) { nav.push(Screen.SettingsScan) },
        SettingsEntry(R.string.library_folders, R.string.library_folders_summary, AppIcons.Folder, Color(0xFF2F6B4A)) { nav.push(Screen.LibraryFolders) },
        SettingsEntry(R.string.hidden_songs, R.string.settings_card_hidden, AppIcons.VisibilityOff, Color(0xFF5E4B7A)) { nav.push(Screen.HiddenSongs) },
    ),
)

/** Library > Scanning: how many songs were found, rescan, and the scanner type. */
@Composable
fun ScanSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val fastScanner by viewModel.settings.useMediaStoreScanner.collectAsStateWithLifecycle()
    SettingsPage(R.string.settings_scan, nav) {
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

    }
}

/** Navigation > Tab order. */
@Composable
fun TabOrderSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val tabOrderRaw by viewModel.settings.tabOrder.collectAsStateWithLifecycle()
    val tabOrder = remember(tabOrderRaw) { TabOrder.parse(tabOrderRaw) }
    SettingsPage(R.string.settings_tab_order, nav) {
        // Tab order: saved at once, drives the bottom navigation (Search stays fixed on the left).
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

    }
}

/** Navigation > Song gestures. */
@Composable
fun GesturesSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    SettingsPage(R.string.settings_gestures, nav) {
        // Song-row gestures. With swipe off the gesture does nothing; the queue button is the alternative.
        val swipeOn by viewModel.settings.swipeActionsEnabled.collectAsStateWithLifecycle()
        val queueButtonOn by viewModel.settings.queueButtonEnabled.collectAsStateWithLifecycle()
        SectionHeader(stringResource(R.string.settings_gestures))
        GestureSwitchRow(R.string.settings_swipe_actions, R.string.settings_swipe_actions_note, swipeOn, true) {
            viewModel.settings.setSwipeActionsEnabled(it)
        }
        GestureSwitchRow(R.string.settings_queue_button, R.string.settings_queue_button_note, queueButtonOn && !swipeOn, !swipeOn) {
            viewModel.settings.setQueueButtonEnabled(it)
        }
    }
}

// ------------------------------------------------------------------ Lyrics

/** Lyrics: text alignment (left / centre / right) and the lyrics font, with a live preview. */
@Composable
fun LyricsSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val settings = viewModel.settings
    val alignment by settings.lyricsAlignment.collectAsStateWithLifecycle()
    val choices by settings.fontChoices.collectAsStateWithLifecycle()
    val customVersion by settings.customFontVersion.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as LocalPlayerApp).container
    val customFile = container.customFont.file
    val hasCustom = customVersion != 0L && container.customFont.exists()
    val choice = choices[FontCategory.LYRICS]
    val family = remember(choice, customVersion) { AppFonts.familyFor(choice, customFile) }
    var editingFont by remember { mutableStateOf(false) }

    SettingsPage(R.string.settings_lyrics, nav) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.lyrics_alignment), style = MaterialTheme.typography.bodyLarge)
            LyricsAlignment.entries.forEach { option ->
                RadioLine(
                    stringResource(
                        when (option) {
                            LyricsAlignment.START -> R.string.lyrics_align_start
                            LyricsAlignment.CENTER -> R.string.lyrics_align_center
                            LyricsAlignment.END -> R.string.lyrics_align_end
                        },
                    ),
                    alignment == option,
                ) { viewModel.setLyricsAlignment(option) }
            }
        }
        NavRow(
            title = stringResource(R.string.lyrics_font),
            subtitle = stringResource(choiceLabel(choice)),
            onClick = { editingFont = true },
        )
        SectionHeader(stringResource(R.string.color_preview))
        Text(
            stringResource(R.string.lyrics_preview_text),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = family ?: androidx.compose.ui.text.font.FontFamily.Default,
                textAlign = alignment.toTextAlign(),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }

    if (editingFont) {
        FontPickerDialog(
            category = FontCategory.LYRICS,
            current = choice,
            hasCustom = hasCustom,
            customFile = customFile,
            onApply = {
                viewModel.setFont(FontCategory.LYRICS, it)
                editingFont = false
            },
            onDismiss = { editingFont = false },
        )
    }
}

fun LyricsAlignment.toTextAlign(): TextAlign = when (this) {
    LyricsAlignment.START -> TextAlign.Start
    LyricsAlignment.CENTER -> TextAlign.Center
    LyricsAlignment.END -> TextAlign.End
}

// ------------------------------------------------------------------ Your Mix

/** Your Mix hub: general, how the Mix is built, and the options. */
@Composable
fun YourMixSettingsScreen(nav: NavState, @Suppress("UNUSED_PARAMETER") viewModel: LibraryViewModel) = SettingsHub(
    R.string.settings_your_mix, nav,
    listOf(
        SettingsEntry(R.string.settings_mix_general, R.string.settings_card_mix_general, AppIcons.Dashboard, Color(0xFF7A3E5A)) { nav.push(Screen.SettingsMixGeneral) },
        SettingsEntry(R.string.mix_strategy_title, R.string.settings_card_mix_strategy, AppIcons.Shuffle, Color(0xFF9A4A3A)) { nav.push(Screen.SettingsMixStrategy) },
        SettingsEntry(R.string.mix_options_title, R.string.settings_card_mix_options, AppIcons.Tune, Color(0xFF3A5A9A)) { nav.push(Screen.SettingsMixOptions) },
    ),
)

@Composable
fun MixGeneralSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val dismissed by viewModel.settings.yourMixCardDismissed.collectAsStateWithLifecycle()
    SettingsPage(R.string.settings_mix_general, nav) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.your_mix_show_card), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.your_mix_show_card_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = !dismissed, onCheckedChange = { viewModel.setYourMixCardDismissed(!it) })
        }
        NavRow(
            title = stringResource(R.string.mix_open),
            subtitle = stringResource(R.string.settings_your_mix_note),
            onClick = { nav.push(Screen.YourMix) },
        )

    }
}

@Composable
fun MixStrategySettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val mix by viewModel.settings.mixSettings.collectAsStateWithLifecycle()
    SettingsPage(R.string.mix_strategy_title, nav) {
        SectionHeader(stringResource(R.string.mix_strategy_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            MixStrategy.entries.forEach { option ->
                Column(Modifier.fillMaxWidth()) {
                    RadioLine(stringResource(strategyLabel(option)), mix.strategy == option) {
                        viewModel.setMixSettings(mix.copy(strategy = option))
                    }
                    Text(
                        stringResource(
                            when (option) {
                                MixStrategy.MOST_PLAYED -> R.string.mix_strategy_most_played_note
                                MixStrategy.FORGOTTEN -> R.string.mix_strategy_forgotten_note
                                MixStrategy.RANDOM -> R.string.mix_strategy_random_note
                                MixStrategy.SMART -> R.string.mix_strategy_smart_note
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 52.dp, bottom = 4.dp),
                    )
                }
            }
        }

    }
}

@Composable
fun MixOptionsSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val mix by viewModel.settings.mixSettings.collectAsStateWithLifecycle()
    SettingsPage(R.string.mix_options_title, nav) {
        SectionHeader(stringResource(R.string.mix_options_title))
        Column(Modifier.padding(horizontal = 16.dp)) {
            MixSlider(
                label = stringResource(R.string.mix_size, mix.size),
                value = mix.size.toFloat(),
                range = MixSettings.MIN_SIZE.toFloat()..MixSettings.MAX_SIZE.toFloat(),
                steps = (MixSettings.MAX_SIZE - MixSettings.MIN_SIZE) / 5 - 1,
            ) { viewModel.setMixSettings(mix.copy(size = (it / 5).roundToInt() * 5)) }

            Text(stringResource(R.string.mix_window_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.mix_window_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MixSettings.WINDOWS.forEach { days ->
                RadioLine(
                    if (days == 0) stringResource(R.string.mix_window_all) else stringResource(R.string.mix_window_days, days),
                    mix.windowDays == days,
                ) { viewModel.setMixSettings(mix.copy(windowDays = days)) }
            }

            MixSlider(
                label = stringResource(R.string.mix_recent_weight, mix.recentWeight),
                note = stringResource(R.string.mix_recent_weight_note),
                value = mix.recentWeight.toFloat(),
                range = 0f..100f,
                steps = 9,
            ) { viewModel.setMixSettings(mix.copy(recentWeight = (it / 10).roundToInt() * 10)) }

            MixSlider(
                label = stringResource(R.string.mix_variety, mix.variety),
                note = stringResource(R.string.mix_variety_note),
                value = mix.variety.toFloat(),
                range = 0f..100f,
                steps = 9,
            ) { viewModel.setMixSettings(mix.copy(variety = (it / 10).roundToInt() * 10)) }

            MixSlider(
                label = if (mix.avoidRecentDays == 0) stringResource(R.string.mix_avoid_off) else stringResource(R.string.mix_avoid_days, mix.avoidRecentDays),
                note = stringResource(R.string.mix_avoid_note),
                value = mix.avoidRecentDays.toFloat(),
                range = 0f..MixSettings.MAX_AVOID_DAYS.toFloat(),
                steps = MixSettings.MAX_AVOID_DAYS - 1,
            ) { viewModel.setMixSettings(mix.copy(avoidRecentDays = it.roundToInt())) }
        }
    }
}

@Composable
private fun MixSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, note: String? = null, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

// ------------------------------------------------------------------ About

@Composable
fun AboutSettingsScreen(nav: NavState, container: com.localplayer.music.AppContainer) {
    val context = LocalContext.current
    val update: com.localplayer.music.ui.UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "about-update",
        factory = viewModelFactory {
            initializer { com.localplayer.music.ui.UpdateViewModel(container) }
        },
    )
    val ui by update.state.collectAsStateWithLifecycle()
    val auto by container.settings.autoUpdateCheck.collectAsStateWithLifecycle()
    val version = remember(context) { com.localplayer.music.data.update.AppInfo.versionName(context) }
    val repoUrl = remember(context) { com.localplayer.music.data.update.AppInfo.repoUrl(context) }
    val known = remember { update.knownNewer() }

    SettingsPage(R.string.settings_about, nav) {
        Text(
            stringResource(R.string.version_label, version),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (repoUrl != null) {
            NavRow(
                title = stringResource(R.string.about_github),
                subtitle = repoUrl.removePrefix("https://"),
                onClick = { openWebPage(context, repoUrl) },
            )
        } else {
            Text(
                stringResource(R.string.about_github_unset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.update_auto_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = auto, onCheckedChange = { container.settings.setAutoUpdateCheck(it) })
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val busy = ui is com.localplayer.music.ui.UpdateUi.Checking || ui is com.localplayer.music.ui.UpdateUi.Downloading
            Button(onClick = update::check, enabled = !busy && repoUrl != null) {
                Text(stringResource(R.string.update_check))
            }
            if (known != null && ui is com.localplayer.music.ui.UpdateUi.Idle) {
                Text(stringResource(R.string.update_known, known), style = MaterialTheme.typography.bodyMedium)
            }
            UpdateStatus(ui, update, context)
        }
    }
}

@Composable
private fun UpdateStatus(
    ui: com.localplayer.music.ui.UpdateUi,
    update: com.localplayer.music.ui.UpdateViewModel,
    context: android.content.Context,
) {
    val err = MaterialTheme.colorScheme.error
    when (ui) {
        com.localplayer.music.ui.UpdateUi.Idle -> Unit
        com.localplayer.music.ui.UpdateUi.Checking -> Text(stringResource(R.string.update_checking))
        is com.localplayer.music.ui.UpdateUi.UpToDate -> Text(stringResource(R.string.update_up_to_date, ui.latest))
        com.localplayer.music.ui.UpdateUi.NotConfigured -> Text(stringResource(R.string.about_github_unset), color = err)
        com.localplayer.music.ui.UpdateUi.NoRelease -> Text(stringResource(R.string.update_no_release))
        com.localplayer.music.ui.UpdateUi.Offline -> Text(stringResource(R.string.update_offline), color = err)
        com.localplayer.music.ui.UpdateUi.RateLimited -> Text(stringResource(R.string.update_rate_limited), color = err)
        is com.localplayer.music.ui.UpdateUi.Failed -> Text(stringResource(R.string.update_failed, ui.code.toString()), color = err)
        is com.localplayer.music.ui.UpdateUi.Available -> {
            val r = ui.release
            Text(stringResource(R.string.update_available, r.tag), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.update_installed, com.localplayer.music.data.update.AppInfo.versionName(context)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (r.title.isNotBlank() && r.title != r.tag) Text(r.title, style = MaterialTheme.typography.bodyMedium)
            if (r.notes.isNotBlank()) {
                Text(stringResource(R.string.update_notes), style = MaterialTheme.typography.labelLarge)
                Text(
                    r.notes.take(4000),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
            if (r.apkUrl != null) {
                Button(onClick = { update.download(r) }) { Text(stringResource(R.string.update_download)) }
                Text(stringResource(R.string.update_install_note), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.update_no_apk), color = err)
            }
            OutlinedButton(onClick = { openWebPage(context, r.pageUrl) }) { Text(stringResource(R.string.update_open_page)) }
        }
        is com.localplayer.music.ui.UpdateUi.Downloading -> {
            Text(stringResource(R.string.update_downloading, ui.release.tag))
            androidx.compose.material3.LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
        }
        is com.localplayer.music.ui.UpdateUi.Downloaded -> {
            Text(stringResource(R.string.update_downloaded, ui.release.tag))
            Button(onClick = { installApk(context, ui.file) }) { Text(stringResource(R.string.update_install)) }
            Text(stringResource(R.string.update_install_note), style = MaterialTheme.typography.bodySmall)
        }
        is com.localplayer.music.ui.UpdateUi.DownloadFailed -> {
            Text(stringResource(if (ui.corrupt) R.string.update_corrupt else R.string.update_download_failed), color = err)
            OutlinedButton(onClick = { update.backToAvailable(ui.release) }) { Text(stringResource(R.string.ls_retry)) }
        }
    }
}

private fun openWebPage(context: android.content.Context, url: String) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, url, android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Hands the downloaded APK to Android's package installer, which shows its own confirmation. When this app is not yet
 * allowed to install packages, Android's "install unknown apps" page for THIS app opens first; after allowing, press
 * Install again. Nothing is installed without the user's confirmation, and the system only accepts an APK signed
 * with the same key as the installed app.
 */
private fun installApk(context: android.content.Context, file: java.io.File) {
    try {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, R.string.update_install_failed, android.widget.Toast.LENGTH_LONG).show()
    }
}

/** A titled switch with a note; [enabled] = false greys it out (it cannot be changed). */
@Composable
private fun GestureSwitchRow(title: Int, note: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dim = if (enabled) 1f else 0.5f
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(dim))
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(dim),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
