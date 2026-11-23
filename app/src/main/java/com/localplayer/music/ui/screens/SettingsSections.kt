package com.localplayer.music.ui.screens

import androidx.core.net.toUri
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.semantics.semantics
import com.localplayer.music.data.mix.MixSettings
import com.localplayer.music.data.mix.MixStrategy
import kotlin.math.roundToInt
import com.localplayer.music.data.personalization.ActionRowSide
import com.localplayer.music.data.personalization.ActionRowStyle
import com.localplayer.music.data.personalization.BackButtonSide
import com.localplayer.music.data.personalization.FontCategory
import com.localplayer.music.data.personalization.LyricsAlignment
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsLinkRow
import com.localplayer.music.ui.components.SettingsRadioRow
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.components.SettingsSwitchRow
import com.localplayer.music.ui.components.SettingsSliderRow
import com.localplayer.music.ui.components.TabOrder
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.nav.Screen
import com.localplayer.music.ui.theme.AppFonts

/** Shared frame of every settings sub-screen: back arrow + title + scrolling content with room for the bottom bars. */
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
fun AppearanceSettingsScreen(nav: NavState, viewModel: LibraryViewModel) = SettingsHub(
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
    SettingsScaffold(title = stringResource(R.string.settings_theme), onBack = { nav.pop() }) {
        SettingsGroup(note = if (pure.isOn) stringResource(R.string.theme_forced_by_pure) else null) {
            ThemeMode.entries.forEach { mode ->
                item {
                    SettingsRadioRow(
                        title = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        selected = themeMode == mode,
                        enabled = !pure.isOn,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Language

@Composable
fun LanguageSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val language by viewModel.settings.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_language), onBack = { nav.pop() }) {
        SettingsGroup {
            AppLanguage.entries.forEach { option ->
                item {
                    // Each language is always shown in its own language.
                    SettingsRadioRow(if (option == AppLanguage.ENGLISH) "English" else "Ελληνικά", language == option, {
                        if (language != option) {
                            viewModel.setLanguage(option)
                            // Resources are resolved per activity context: recreate to apply immediately.
                            (context as? android.app.Activity)?.recreate()
                        }
                    })
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
        SettingsEntry(R.string.duplicates_title, R.string.duplicates_card, AppIcons.ContentCopy, Color(0xFF8A5A1F)) { nav.push(Screen.Duplicates) },
    ),
)

/** Library > Scanning: how many songs were found, rescan, and the scanner type. */
@Composable
fun ScanSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val fastScanner by viewModel.settings.useMediaStoreScanner.collectAsStateWithLifecycle()
    val supported = AppSettings.mediaStoreScannerSupported()
    SettingsScaffold(title = stringResource(R.string.settings_scan), onBack = { nav.pop() }) {
        SettingsGroup(note = stringResource(R.string.settings_rescan_note)) {
            songs?.let {
                item { SettingsCustomRow { Text(pluralStringResource(R.plurals.songs_in_library, it.size, it.size), style = MaterialTheme.typography.bodyLarge) } }
            }
            if (scan.isScanning) item { SettingsCustomRow { ScanBanner(scan) } }
            if (!scan.isScanning && scan.error != null) item { SettingsCustomRow { ScanErrorBanner(scan, onRetry = viewModel::rescan, onRemoveFolder = viewModel::removeLibraryFolder, onRelinkFolder = viewModel::relinkLibraryFolder) } }
            if (!scan.isScanning && scan.unreadable > 0) {
                item { SettingsCustomRow { Text(pluralStringResource(R.plurals.scan_unreadable, scan.unreadable, scan.unreadable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            item {
                SettingsCustomRow {
                    Button(onClick = viewModel::rescan, enabled = !scan.isScanning, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_rescan))
                    }
                }
            }
        }
        // Scanner choice: OFF = normal scanner, ON = MediaStore-only fast scanner (Android 11+).
        SettingsGroup {
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_scanner_title), fastScanner, viewModel::setUseMediaStoreScanner,
                    note = stringResource(R.string.settings_scanner_description) +
                        if (!supported) "\n" + stringResource(R.string.settings_scanner_unsupported) else "",
                    enabled = supported && !scan.isScanning,
                )
            }
        }
    }
}

/** Navigation > Tab order: drag a card by its three-line handle to the place it should have. */
@Composable
fun TabOrderSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val tabOrderRaw by viewModel.settings.tabOrder.collectAsStateWithLifecycle()
    val tabOrder = remember(tabOrderRaw) { TabOrder.parse(tabOrderRaw) }
    // What is drawn: follows the saved order, but a drop is shown at once (no flicker while the setting round-trips).
    var shown by remember(tabOrder) { mutableStateOf(tabOrder) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeight = 64.dp
    val gap = 8.dp
    val pitch = with(density) { (rowHeight + gap).toPx() }
    val haptics = com.localplayer.music.ui.theme.rememberHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dragging by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val count = shown.size
    // The slot the dragged card would land in right now (read when called, so a long-lived gesture sees fresh values).
    fun targetSlot(): Int = if (dragging < 0) -1 else (dragging + (dragY / pitch).roundToInt()).coerceIn(0, count - 1)
    val target = targetSlot()
    var lastTick by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(target, dragging) {
        if (dragging < 0) lastTick = -1
        else {
            if (lastTick != -1 && target != lastTick) haptics.threshold()
            lastTick = target
        }
    }
    val pure = com.localplayer.music.ui.theme.LocalPureMode.current.isOn

    fun finishDrag() {
        val from = dragging
        if (from < 0) return
        val to = targetSlot()
        scope.launch {
            androidx.compose.animation.core.animate(dragY, (to - from) * pitch, animationSpec = androidx.compose.animation.core.tween(160)) { v, _ -> dragY = v }
            if (to != from) {
                val moved = TabOrder.move(shown, from, to - from)
                shown = moved
                viewModel.setTabOrder(moved)
            }
            dragging = -1
            dragY = 0f
        }
    }

    SettingsScaffold(title = stringResource(R.string.settings_tab_order), onBack = { nav.pop() }) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            shown.forEachIndexed { index, tab ->
                // A new composition when a card changes place, so its slide state starts at rest in the new slot.
                androidx.compose.runtime.key(tab, index) {
                    val name = stringResource(tab.label)
                    val isDragged = index == dragging
                    val shift = when {
                        dragging < 0 || isDragged -> 0f
                        dragging < index && index <= target -> -pitch
                        target <= index && index < dragging -> pitch
                        else -> 0f
                    }
                    val slide by androidx.compose.animation.core.animateFloatAsState(
                        shift,
                        androidx.compose.animation.core.spring(stiffness = 700f),
                        label = "tabSlide",
                    )
                    val up = stringResource(R.string.move_tab_up, name)
                    val down = stringResource(R.string.move_tab_down, name)
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = if (pure) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) dragY else slide
                                val lift = if (isDragged) 1f else 0f
                                scaleX = 1f + 0.02f * lift
                                scaleY = 1f + 0.02f * lift
                                shadowElevation = 12.dp.toPx() * lift
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                            }
                            .semantics {
                                customActions = listOf(
                                    androidx.compose.ui.semantics.CustomAccessibilityAction(up) {
                                        if (index > 0) { val m = TabOrder.move(shown, index, -1); shown = m; viewModel.setTabOrder(m); true } else false
                                    },
                                    androidx.compose.ui.semantics.CustomAccessibilityAction(down) {
                                        if (index < count - 1) { val m = TabOrder.move(shown, index, +1); shown = m; viewModel.setTabOrder(m); true } else false
                                    },
                                )
                            },
                    ) {
                        Row(Modifier.fillMaxSize().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            // Three-line handle, 56 dp touch target: the drag starts here.
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragging = index
                                                dragY = 0f
                                                haptics.threshold()
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragY = (dragY + amount.y).coerceIn(-index * pitch, (count - 1 - index) * pitch)
                                            },
                                            onDragEnd = { finishDrag() },
                                            onDragCancel = { finishDrag() },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        SettingsGroup(note = stringResource(R.string.settings_tab_order_note_drag)) {
            item { SettingsLinkRow(stringResource(R.string.settings_tab_order_reset), onClick = { viewModel.resetTabOrder() }, enabled = tabOrderRaw != null) }
        }
    }
}

/** Navigation > Song gestures. */
@Composable
fun GesturesSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    // Song-row gestures. With swipe off the gesture does nothing; the queue button is the alternative.
    val swipeOn by viewModel.settings.swipeActionsEnabled.collectAsStateWithLifecycle()
    val queueButtonOn by viewModel.settings.queueButtonEnabled.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_gestures), onBack = { nav.pop() }) {
        SettingsGroup {
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_swipe_actions), swipeOn, viewModel.settings::setSwipeActionsEnabled,
                    note = stringResource(R.string.settings_swipe_actions_note),
                )
            }
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_queue_button), queueButtonOn && !swipeOn, viewModel.settings::setQueueButtonEnabled,
                    note = stringResource(R.string.settings_queue_button_note), enabled = !swipeOn,
                )
            }
        }
    }
}

/** One-handed use: the back button stays in the top bar, or floats above the mini player on the right / left. */
@Composable
fun BackButtonSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val side by viewModel.settings.backButtonSide.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_back_button), onBack = { nav.pop() }) {
        SettingsGroup(title = stringResource(R.string.back_side_group)) {
            BackButtonSide.entries.forEach { option ->
                item {
                    SettingsRadioRow(
                        stringResource(
                            when (option) {
                                BackButtonSide.OFF -> R.string.back_side_off
                                BackButtonSide.RIGHT -> R.string.back_side_right
                                BackButtonSide.LEFT -> R.string.back_side_left
                            },
                        ),
                        side == option,
                        { viewModel.settings.setBackButtonSide(option) },
                        note = stringResource(
                            when (option) {
                                BackButtonSide.OFF -> R.string.back_side_off_note
                                BackButtonSide.RIGHT -> R.string.back_side_right_note
                                BackButtonSide.LEFT -> R.string.back_side_left_note
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** Look and side of the Shuffle / History row of the library tabs. */
@Composable
fun ActionRowSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val style by viewModel.settings.actionRowStyle.collectAsStateWithLifecycle()
    val side by viewModel.settings.actionRowSide.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_action_row), onBack = { nav.pop() }) {
        SettingsGroup(title = stringResource(R.string.action_row_style_group)) {
            ActionRowStyle.entries.forEach { option ->
                item {
                    SettingsRadioRow(
                        stringResource(if (option == ActionRowStyle.PILL) R.string.action_row_pill else R.string.action_row_classic),
                        style == option,
                        { viewModel.settings.setActionRowStyle(option) },
                        note = stringResource(
                            if (option == ActionRowStyle.PILL) R.string.action_row_pill_note else R.string.action_row_classic_note,
                        ),
                    )
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.action_row_side_group), note = stringResource(R.string.action_row_side_note)) {
            ActionRowSide.entries.forEach { option ->
                item {
                    SettingsRadioRow(
                        stringResource(
                            when (option) {
                                ActionRowSide.LEFT -> R.string.action_row_side_left
                                ActionRowSide.RIGHT -> R.string.action_row_side_right
                            },
                        ),
                        side == option,
                        { viewModel.settings.setActionRowSide(option) },
                        enabled = style == ActionRowStyle.PILL,
                    )
                }
            }
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

    val inPlayer by settings.lyricsInPlayer.collectAsStateWithLifecycle()
    val fill by settings.lyricsFill.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_lyrics), onBack = { nav.pop() }) {
        SettingsGroup(title = stringResource(R.string.lyrics_alignment)) {
            LyricsAlignment.entries.forEach { option ->
                item {
                    SettingsRadioRow(
                        stringResource(
                            when (option) {
                                LyricsAlignment.START -> R.string.lyrics_align_start
                                LyricsAlignment.CENTER -> R.string.lyrics_align_center
                                LyricsAlignment.END -> R.string.lyrics_align_end
                            },
                        ),
                        alignment == option,
                        { viewModel.setLyricsAlignment(option) },
                    )
                }
            }
        }
        SettingsGroup {
            item {
                SettingsSwitchRow(
                    stringResource(R.string.lyrics_in_player), inPlayer, settings::setLyricsInPlayer,
                    note = stringResource(R.string.lyrics_in_player_note),
                )
            }
            item {
                SettingsSwitchRow(
                    stringResource(R.string.lyrics_fill), fill, settings::setLyricsFill,
                    note = stringResource(R.string.lyrics_fill_note),
                )
            }
            item {
                SettingsLinkRow(
                    stringResource(R.string.lyrics_font),
                    onClick = { editingFont = true },
                    summary = stringResource(choiceLabel(choice)),
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.color_preview)) {
            item {
                SettingsCustomRow {
                    Text(
                        stringResource(R.string.lyrics_preview_text),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = family ?: androidx.compose.ui.text.font.FontFamily.Default,
                            textAlign = alignment.toTextAlign(),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
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
fun YourMixSettingsScreen(nav: NavState, viewModel: LibraryViewModel) = SettingsHub(
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
    SettingsScaffold(title = stringResource(R.string.settings_mix_general), onBack = { nav.pop() }) {
        SettingsGroup(note = stringResource(R.string.settings_your_mix_note)) {
            item {
                SettingsSwitchRow(
                    stringResource(R.string.your_mix_show_card), !dismissed, { viewModel.setYourMixCardDismissed(!it) },
                    note = stringResource(R.string.your_mix_show_card_note),
                )
            }
            item { SettingsLinkRow(stringResource(R.string.mix_open), onClick = { nav.push(Screen.YourMix) }) }
        }
    }
}

@Composable
fun MixStrategySettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val mix by viewModel.settings.mixSettings.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.mix_strategy_title), onBack = { nav.pop() }) {
        SettingsGroup {
            MixStrategy.entries.forEach { option ->
                item {
                    SettingsRadioRow(
                        stringResource(strategyLabel(option)),
                        mix.strategy == option,
                        { viewModel.setMixSettings(mix.copy(strategy = option)) },
                        note = stringResource(
                            when (option) {
                                MixStrategy.MOST_PLAYED -> R.string.mix_strategy_most_played_note
                                MixStrategy.FORGOTTEN -> R.string.mix_strategy_forgotten_note
                                MixStrategy.RANDOM -> R.string.mix_strategy_random_note
                                MixStrategy.SMART -> R.string.mix_strategy_smart_note
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun MixOptionsSettingsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val mix by viewModel.settings.mixSettings.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.mix_options_title), onBack = { nav.pop() }) {
        SettingsGroup {
            item {
                SettingsSliderRow(
                    title = stringResource(R.string.mix_size_title),
                    valueText = mix.size.toString(),
                    value = mix.size.toFloat(),
                    range = MixSettings.MIN_SIZE.toFloat()..MixSettings.MAX_SIZE.toFloat(),
                    steps = (MixSettings.MAX_SIZE - MixSettings.MIN_SIZE) / 5 - 1,
                    onChange = { viewModel.setMixSettings(mix.copy(size = (it / 5).roundToInt() * 5)) },
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(R.string.mix_recent_weight_title),
                    valueText = "${mix.recentWeight}%",
                    note = stringResource(R.string.mix_recent_weight_note),
                    value = mix.recentWeight.toFloat(),
                    range = 0f..100f,
                    steps = 9,
                    onChange = { viewModel.setMixSettings(mix.copy(recentWeight = (it / 10).roundToInt() * 10)) },
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(R.string.mix_variety_title),
                    valueText = "${mix.variety}%",
                    note = stringResource(R.string.mix_variety_note),
                    value = mix.variety.toFloat(),
                    range = 0f..100f,
                    steps = 9,
                    onChange = { viewModel.setMixSettings(mix.copy(variety = (it / 10).roundToInt() * 10)) },
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(R.string.mix_avoid_title),
                    valueText = if (mix.avoidRecentDays == 0) stringResource(R.string.mix_avoid_off_short) else pluralStringResource(R.plurals.mix_avoid_days_short, mix.avoidRecentDays, mix.avoidRecentDays),
                    note = stringResource(R.string.mix_avoid_note),
                    value = mix.avoidRecentDays.toFloat(),
                    range = 0f..MixSettings.MAX_AVOID_DAYS.toFloat(),
                    steps = MixSettings.MAX_AVOID_DAYS - 1,
                    onChange = { viewModel.setMixSettings(mix.copy(avoidRecentDays = it.roundToInt())) },
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.mix_window_title), note = stringResource(R.string.mix_window_note)) {
            MixSettings.WINDOWS.forEach { days ->
                item {
                    SettingsRadioRow(
                        if (days == 0) stringResource(R.string.mix_window_all) else pluralStringResource(R.plurals.mix_window_days, days, days),
                        mix.windowDays == days,
                        { viewModel.setMixSettings(mix.copy(windowDays = days)) },
                    )
                }
            }
        }
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
    val beta by container.settings.includeBetaUpdates.collectAsStateWithLifecycle()
    val version = remember(context) { com.localplayer.music.data.update.AppInfo.versionName(context) }
    val repoUrl = remember(context) { com.localplayer.music.data.update.AppInfo.repoUrl(context) }
    val known = remember { update.knownNewer() }

    SettingsScaffold(title = stringResource(R.string.settings_about), onBack = { nav.pop() }) {
        SettingsGroup(note = if (repoUrl == null) stringResource(R.string.about_github_unset) else null) {
            item { SettingsCustomRow { Text(stringResource(R.string.version_label, version), style = MaterialTheme.typography.bodyLarge) } }
            if (repoUrl != null) {
                item {
                    SettingsLinkRow(
                        title = stringResource(R.string.about_github),
                        summary = repoUrl.removePrefix("https://"),
                        onClick = { openWebPage(context, repoUrl) },
                    )
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.update_check)) {
            item {
                SettingsSwitchRow(stringResource(R.string.update_auto), auto, container.settings::setAutoUpdateCheck, note = stringResource(R.string.update_auto_note))
            }
            item {
                SettingsSwitchRow(stringResource(R.string.update_beta), beta, container.settings::setIncludeBetaUpdates, note = stringResource(R.string.update_beta_note))
            }
            item {
                SettingsCustomRow {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val busy = ui is com.localplayer.music.ui.UpdateUi.Checking || ui is com.localplayer.music.ui.UpdateUi.Downloading
                        Button(onClick = update::check, enabled = !busy && repoUrl != null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_check))
                        }
                        if (known != null && ui is com.localplayer.music.ui.UpdateUi.Idle) {
                            Text(stringResource(R.string.update_known, known), style = MaterialTheme.typography.bodyMedium)
                        }
                        UpdateStatus(ui, update, context)
                    }
                }
            }
        }
        CrashReportsSection()
    }
}

/** Opt-in crash reports: a switch (off by default); when on, a saved report can be shared or cleared. Nothing is sent by the app. */
@Composable
private fun CrashReportsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(com.localplayer.music.util.CrashReports.isEnabled(context)) }
    var report by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(enabled) {
        report = if (enabled) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.localplayer.music.util.CrashReports.read(context) }
        } else null
    }
    val saved = report
    SettingsGroup {
        item {
            SettingsSwitchRow(
                stringResource(R.string.crash_reports), enabled,
                { enabled = it; com.localplayer.music.util.CrashReports.setEnabled(context, it) },
                note = stringResource(R.string.crash_reports_note),
            )
        }
        if (enabled && saved != null) {
            item {
                SettingsCustomRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, saved.takeLast(60_000))
                            runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                        }) { Text(stringResource(R.string.crash_reports_share)) }
                        androidx.compose.material3.OutlinedButton(onClick = {
                            com.localplayer.music.util.CrashReports.clear(context)
                            report = null
                        }) { Text(stringResource(R.string.crash_reports_clear)) }
                    }
                }
            }
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
            com.localplayer.music.ui.components.ChunkyProgress(ui.progress)
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
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
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
                    "package:${context.packageName}".toUri(),
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
