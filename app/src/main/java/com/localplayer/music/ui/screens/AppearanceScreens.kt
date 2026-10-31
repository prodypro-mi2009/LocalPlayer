package com.localplayer.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localplayer.music.LocalPlayerApp
import com.localplayer.music.R
import com.localplayer.music.data.personalization.ColorMath
import com.localplayer.music.data.personalization.ControlColorGroup
import com.localplayer.music.data.personalization.FontCategory
import com.localplayer.music.data.personalization.FontChoice
import com.localplayer.music.data.personalization.PureMode
import com.localplayer.music.ui.LibraryViewModel
import com.localplayer.music.ui.components.AppIcons
import com.localplayer.music.ui.components.DetailTopBar
import com.localplayer.music.ui.components.SettingsCustomRow
import com.localplayer.music.ui.components.SettingsGroup
import com.localplayer.music.ui.components.SettingsLinkRow
import com.localplayer.music.ui.components.SettingsNote
import com.localplayer.music.ui.components.SettingsRadioRow
import com.localplayer.music.ui.components.SettingsScaffold
import com.localplayer.music.ui.components.SettingsSliderRow
import com.localplayer.music.ui.components.SettingsSwitchRow
import com.localplayer.music.ui.components.LocalBottomBarInset
import com.localplayer.music.ui.components.SectionHeader
import com.localplayer.music.ui.nav.NavState
import com.localplayer.music.ui.theme.AppColors
import com.localplayer.music.ui.theme.AppFonts
import com.localplayer.music.ui.theme.onColorFor
import java.util.Locale

// ---------------------------------------------------------------- Colors

@Composable
fun ColorsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val settings = viewModel.settings
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val artworkColors by settings.artworkColors.collectAsStateWithLifecycle()
    val artworkBlur by settings.artworkBlur.collectAsStateWithLifecycle()
    val blurEverywhere by settings.blurEverywhere.collectAsStateWithLifecycle()
    val backgroundVersion by settings.backgroundVersion.collectAsStateWithLifecycle()
    val backgroundDim by settings.backgroundDim.collectAsStateWithLifecycle()
    val controlColors by settings.controlColors.collectAsStateWithLifecycle()
    val pureMode by settings.pureMode.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as LocalPlayerApp).container
    val background by produceState<ImageBitmap?>(initialValue = null, backgroundVersion) {
        value = if (backgroundVersion == 0L) null else container.background.load()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setBackground(uri)
    }
    var editing by remember { mutableStateOf<ControlColorGroup?>(null) }

    SettingsScaffold(title = stringResource(R.string.settings_colors), onBack = { nav.pop() }) {
        val pureOn = pureMode.isOn
        SettingsGroup {
            item {
                SettingsSwitchRow(
                    stringResource(R.string.settings_dynamic_color), dynamicColor, viewModel::setDynamicColor,
                    enabled = !pureOn,
                )
            }
        }

        // Background made from the album art: exactly one choice. Only the chosen one runs; the others are not
        // even read (no colour is extracted for the blurred cover, no picture is blurred for the colours).
        SettingsGroup(title = stringResource(R.string.artwork_bg_title), note = stringResource(R.string.artwork_bg_note)) {
            item {
                SettingsRadioRow(
                    stringResource(R.string.artwork_bg_off), !artworkColors && !artworkBlur,
                    { viewModel.setArtworkStyle(colors = false, blur = false) }, enabled = !pureOn,
                )
            }
            item {
                SettingsRadioRow(
                    stringResource(R.string.artwork_bg_color), artworkColors,
                    { viewModel.setArtworkStyle(colors = true, blur = false) }, enabled = !pureOn,
                )
            }
            item {
                SettingsRadioRow(
                    stringResource(R.string.artwork_bg_blur), artworkBlur,
                    { viewModel.setArtworkStyle(colors = false, blur = true) }, enabled = !pureOn,
                )
            }
            item {
                SettingsSwitchRow(
                    stringResource(R.string.artwork_bg_blur_everywhere), blurEverywhere, viewModel::setBlurEverywhere,
                    enabled = artworkBlur && !pureOn,
                )
            }
        }

        // Pure black / pure white: no colour at all; background one uniform colour, every button the exact opposite.
        SettingsGroup(
            title = stringResource(R.string.pure_mode_title),
            note = if (pureOn) stringResource(R.string.pure_mode_active_note) else stringResource(R.string.pure_mode_note),
        ) {
            PureMode.entries.forEach { mode ->
                item {
                    SettingsRadioRow(
                        stringResource(
                            when (mode) {
                                PureMode.OFF -> R.string.pure_off
                                PureMode.BLACK -> R.string.pure_black
                                PureMode.WHITE -> R.string.pure_white
                            },
                        ),
                        pureMode == mode,
                        { viewModel.setPureMode(mode) },
                    )
                }
            }
        }

        // Background picture (unchanged behaviour, moved here from the main settings list).
        SettingsGroup(title = stringResource(R.string.settings_background)) {
            item {
                SettingsCustomRow {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            val image = background
                            if (image != null) {
                                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
                            ) { Icon(AppIcons.Add, contentDescription = stringResource(R.string.background_add)) }
                        }
                    }
                }
            }
            item {
                SettingsSliderRow(
                    title = stringResource(R.string.background_dim),
                    valueText = "${(backgroundDim * 100).toInt()}%",
                    value = backgroundDim,
                    onChange = viewModel::setBackgroundDim,
                )
            }
            if (backgroundVersion != 0L) {
                item {
                    SettingsLinkRow(stringResource(R.string.background_reset), onClick = viewModel::resetBackground)
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.colors_buttons_title), note = stringResource(R.string.colors_buttons_note)) {
            ControlColorGroup.entries.forEach { group ->
                item {
                    val saved = controlColors[group]
                    val name = stringResource(groupLabel(group))
                    SettingsLinkRow(
                        title = name,
                        summary = saved?.let { ColorMath.toHex(it) } ?: stringResource(R.string.color_default),
                        onClick = { editing = group },
                        trailing = { Swatch(saved?.let { Color(it) }, 32.dp) },
                    )
                }
            }
            // The one colour no setting can change.
            item {
                SettingsCustomRow {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.color_favorite_fixed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(AppIcons.Heart, contentDescription = null, tint = AppColors.FavoriteRed, modifier = Modifier.size(32.dp))
                    }
                }
            }
            item {
                SettingsLinkRow(
                    stringResource(R.string.color_reset_all),
                    onClick = viewModel::resetControlColors,
                    enabled = !controlColors.isEmpty,
                )
            }
        }
    }

    editing?.let { group ->
        ColorPickerDialog(
            group = group,
            initial = controlColors[group],
            onApply = {
                viewModel.setControlColor(group, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun groupLabel(group: ControlColorGroup): Int = when (group) {
    ControlColorGroup.PLAY_PAUSE -> R.string.color_group_play_pause
    ControlColorGroup.PREV_NEXT -> R.string.color_group_prev_next
    ControlColorGroup.PLAYBACK_OPTIONS -> R.string.color_group_playback_options
    ControlColorGroup.NOW_PLAYING_TEXT -> R.string.color_group_now_playing_text
    ControlColorGroup.NAVIGATION -> R.string.color_group_navigation
}

/** A round colour sample; null shows an empty ring (= default colour). */
@Composable
private fun Swatch(color: Color?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (color != null) Modifier.background(color) else Modifier)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

private val PresetColors = listOf(
    0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFFE53935, 0xFFFB8C00,
    0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000,
).map { it.toInt() }

/** Where the controls of a group are drawn, so the preview and the contrast check use the right background. */
@Composable
private fun previewBackground(group: ControlColorGroup): Color = when (group) {
    ControlColorGroup.PLAYBACK_OPTIONS -> MaterialTheme.colorScheme.surfaceContainerHigh
    ControlColorGroup.NAVIGATION -> MaterialTheme.colorScheme.surfaceContainer
    else -> MaterialTheme.colorScheme.surface
}

/**
 * Colour picker for one button group: hue / saturation / brightness sliders, a row of ready colours, exact HEX
 * entry and a preview on the real background. Nothing is saved until "Apply".
 */
@Composable
private fun ColorPickerDialog(group: ControlColorGroup, initial: Int?, onApply: (Int?) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: PresetColors[0]
    val startHsv = remember { ColorMath.argbToHsv(start) }
    var hue by remember { mutableStateOf(startHsv[0]) }
    var sat by remember { mutableStateOf(startHsv[1]) }
    var value by remember { mutableStateOf(startHsv[2]) }
    var hexText by remember { mutableStateOf(ColorMath.toHex(start)) }
    val current = ColorMath.hsvToArgb(hue, sat, value)
    val background = previewBackground(group)
    val bg = background.toArgb()
    val effective = ColorMath.ensureContrast(current, bg, group.minContrast)
    val ratio = ColorMath.contrast(current, bg)
    val name = stringResource(groupLabel(group))

    fun setFrom(argb: Int, updateHex: Boolean) {
        val hsv = ColorMath.argbToHsv(argb)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        if (updateHex) hexText = ColorMath.toHex(argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_pick_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preview on the real background.
                Text(stringResource(R.string.color_preview), style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(background).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { GroupPreview(group, Color(effective)) }

                Text(
                    if (effective == current) {
                        stringResource(R.string.color_contrast_ok, String.format(Locale.US, "%.1f", ratio))
                    } else {
                        stringResource(
                            R.string.color_contrast_adjusted,
                            String.format(Locale.US, "%.1f", ratio),
                            ColorMath.toHex(effective),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (effective == current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )

                // Ready colours.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetColors.take(6).forEach { PresetSwatch(it, current == it) { setFrom(it, true) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetColors.drop(6).forEach { PresetSwatch(it, current == it) { setFrom(it, true) } }
                }

                GradientSlider(
                    label = stringResource(R.string.color_hue),
                    value = hue,
                    range = 0f..360f,
                    brush = Brush.horizontalGradient((0..6).map { Color(ColorMath.hsvToArgb(it * 60f, 1f, 1f)) }),
                    onChange = { hue = it; hexText = ColorMath.toHex(ColorMath.hsvToArgb(it, sat, value)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_saturation),
                    value = sat,
                    range = 0f..1f,
                    brush = Brush.horizontalGradient(listOf(Color(ColorMath.hsvToArgb(hue, 0f, value)), Color(ColorMath.hsvToArgb(hue, 1f, value)))),
                    onChange = { sat = it; hexText = ColorMath.toHex(ColorMath.hsvToArgb(hue, it, value)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_brightness),
                    value = value,
                    range = 0f..1f,
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color(ColorMath.hsvToArgb(hue, sat, 1f)))),
                    onChange = { value = it; hexText = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, it)) },
                )

                val parsed = ColorMath.parseHex(hexText)
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text.take(7)
                        ColorMath.parseHex(hexText)?.let { setFrom(it, false) }
                    },
                    singleLine = true,
                    isError = parsed == null,
                    label = { Text(stringResource(R.string.color_hex)) },
                    supportingText = { if (parsed == null) Text(stringResource(R.string.color_hex_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(current) }, enabled = ColorMath.parseHex(hexText) != null) {
                Text(stringResource(R.string.color_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(null) }) { Text(stringResource(R.string.color_reset_group)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun PresetSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val hex = ColorMath.toHex(argb)
    val description = stringResource(R.string.color_swatch_cd, hex)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(argb))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, brush: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(brush))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

/** A small sample of the controls the group colours. */
@Composable
private fun GroupPreview(group: ControlColorGroup, color: Color) {
    when (group) {
        ControlColorGroup.PLAY_PAUSE -> FilledIconButton(
            onClick = {},
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = onColorFor(color)),
            modifier = Modifier.size(64.dp),
        ) { Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(36.dp)) }

        ControlColorGroup.PREV_NEXT -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(AppIcons.SkipPrevious, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
            Icon(AppIcons.SkipNext, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
        }

        ControlColorGroup.PLAYBACK_OPTIONS -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(AppIcons.Shuffle, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Icon(AppIcons.Repeat, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Icon(AppIcons.Timer, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Icon(AppIcons.Lyrics, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        }

        ControlColorGroup.NOW_PLAYING_TEXT -> Text(
            "Αγαπημένο τραγούδι · Artist · 2:49",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )

        ControlColorGroup.NAVIGATION -> Box(
            Modifier.clip(CircleShape).background(color).padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text("Songs", style = MaterialTheme.typography.labelLarge, color = onColorFor(color)) }
    }
}

// ---------------------------------------------------------------- Fonts

@Composable
fun FontsScreen(nav: NavState, viewModel: LibraryViewModel) {
    val settings = viewModel.settings
    val choices by settings.fontChoices.collectAsStateWithLifecycle()
    val customVersion by settings.customFontVersion.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as LocalPlayerApp).container
    val customFile = container.customFont.file
    val hasCustom = customVersion != 0L && container.customFont.exists()
    var editing by remember { mutableStateOf<FontCategory?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCustomFont(uri)
    }

    SettingsScaffold(title = stringResource(R.string.settings_fonts), onBack = { nav.pop() }) {
        SettingsGroup(note = stringResource(R.string.fonts_note)) {
            FontCategory.entries.forEach { category ->
                item {
                    val choice = choices[category]
                    val family = remember(choice, customVersion) { AppFonts.familyFor(choice, customFile) }
                    val name = stringResource(categoryLabel(category))
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .clickable(role = Role.Button, onClickLabel = name) { editing = category }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(choiceLabel(choice)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.font_sample),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = family ?: FontFamily.Default),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                SettingsLinkRow(stringResource(R.string.font_reset_all), onClick = viewModel::resetFonts, enabled = !choices.isEmpty)
            }
        }

        SettingsGroup(title = stringResource(R.string.font_custom_title), note = stringResource(R.string.font_dot_note)) {
            item {
                SettingsCustomRow {
                    Text(
                        stringResource(R.string.font_custom_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.font_import))
                    }
                }
            }
            if (hasCustom) {
                item { SettingsLinkRow(stringResource(R.string.font_remove), onClick = viewModel::removeCustomFont) }
            }
        }
        SettingsNote(stringResource(R.string.font_licenses))
    }

    editing?.let { category ->
        FontPickerDialog(
            category = category,
            current = choices[category],
            hasCustom = hasCustom,
            customFile = customFile,
            onApply = {
                viewModel.setFont(category, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

internal fun categoryLabel(category: FontCategory): Int = when (category) {
    FontCategory.SONG_TITLE -> R.string.font_category_song_title
    FontCategory.ARTIST -> R.string.font_category_artist
    FontCategory.ALBUM_GENRE -> R.string.font_category_album_genre
    FontCategory.HEADING -> R.string.font_category_heading
    FontCategory.GENERAL -> R.string.font_category_general
    FontCategory.LYRICS -> R.string.font_category_lyrics
}

internal fun choiceLabel(choice: FontChoice): Int = when (choice) {
    FontChoice.DEFAULT -> R.string.font_default
    FontChoice.LIGHT -> R.string.font_light
    FontChoice.MEDIUM -> R.string.font_medium
    FontChoice.BLACK -> R.string.font_black
    FontChoice.CONDENSED -> R.string.font_condensed
    FontChoice.SERIF -> R.string.font_serif
    FontChoice.MONOSPACE -> R.string.font_monospace
    FontChoice.DOT_MATRIX -> R.string.font_dot_matrix
    FontChoice.CUSTOM -> R.string.font_custom
}

/** Every option is shown in its own font (live preview); nothing is saved until "Apply". */
@Composable
internal fun FontPickerDialog(
    category: FontCategory,
    current: FontChoice,
    hasCustom: Boolean,
    customFile: java.io.File,
    onApply: (FontChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    val options = FontChoice.entries.filter { it != FontChoice.CUSTOM || hasCustom }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.font_pick_title, stringResource(categoryLabel(category)))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val family = remember(option) { AppFonts.familyFor(option, customFile) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected = selected == option, role = Role.RadioButton, onClick = { selected = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == option, onClick = null)
                        Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                            Text(stringResource(choiceLabel(option)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.font_sample),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = family ?: FontFamily.Default),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(selected) }) { Text(stringResource(R.string.color_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
