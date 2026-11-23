package com.localplayer.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.localplayer.music.R
import com.localplayer.music.ui.theme.LocalPureMode

/**
 * Shared building blocks for the settings pages, so every page looks and behaves the same:
 * a large page title with a round back button, groups of connected rounded cards (like the main Settings list),
 * and a handful of row types (switch, choice, link, slider, note). Colours come from the Material theme only.
 */

/** A settings page: round back button + big left-aligned title, then a scrollable column of groups. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed area under the scrolling content (e.g. Cancel / Save). When given, the bottom-bar spacer is left out. */
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        SettingsTopBar(title, onBack)
        TopCurvedPanel(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                content()
                // Room for the floating bar (the content scrolls under it) or for the bottom navigation.
                Spacer(Modifier.height(if (bottomBar == null) 4.dp + LocalBottomBarInset.current else 96.dp + barInsetBelowKeyboard()))
            }
            bottomBar?.let { bar -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { bar() } }
        }
    }
}

/**
 * The area under a page title: its content is clipped with rounded top corners, so scrolling content curves away
 * under the title instead of ending in a straight line. It has NO colour of its own: the normal app background
 * (cover tint, blurred cover, background picture) stays visible behind the cards.
 */
@Composable
fun TopCurvedPanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
        content = content,
    )
}

/**
 * Cancel and Save in one floating pill, centred at the bottom; the page scrolls visibly under it. Place it inside
 * a Box/Column that is aligned to the bottom (see [SettingsScaffold] `bottomBar` and the lyrics editor).
 */
@Composable
fun SaveCancelPill(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    cancelEnabled: Boolean = true,
    saveEnabled: Boolean = true,
    /** False hides the pill (it fades and slides away): shown only while there is something to save. */
    visible: Boolean = true,
) {
    // With the floating back button on, the pill sits on the SAME line as that button (the button is at the right or
    // left edge, the pill in the middle); otherwise it floats a little above the bottom bars as before.
    val bottomPad = if (LocalFloatingBack.current) {
        (barInsetBelowKeyboard() - 80.dp).coerceAtLeast(8.dp)
    } else {
        16.dp + barInsetBelowKeyboard()
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 2 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { it / 2 },
    ) {
    Box(
        modifier.fillMaxWidth().padding(bottom = bottomPad),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onCancel,
                    enabled = cancelEnabled,
                    modifier = Modifier.height(52.dp).widthIn(min = 108.dp),
                ) { Text(stringResource(R.string.cancel)) }
                androidx.compose.material3.Button(
                    onClick = onSave,
                    enabled = saveEnabled,
                    modifier = Modifier.height(52.dp).widthIn(min = 108.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
    }
}

/**
 * One action in a floating pill, placed like [SaveCancelPill] (on the same line as the floating back button, or a little
 * above the bottom bars). Used by "Add tracks" ("Add selected (n)"); it shows only while [visible].
 */
@Composable
fun ActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visible: Boolean = true,
) {
    val bottomPad = if (LocalFloatingBack.current) {
        (barInsetBelowKeyboard() - 80.dp).coerceAtLeast(8.dp)
    } else {
        16.dp + barInsetBelowKeyboard()
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 2 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { it / 2 },
    ) {
        Box(modifier.fillMaxWidth().padding(bottom = bottomPad), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(Modifier.padding(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = onClick,
                        enabled = enabled,
                        modifier = Modifier.height(52.dp).widthIn(min = 160.dp),
                    ) { Text(label) }
                }
            }
        }
    }
}

/** The page title bar: round back button + big left-aligned title (also used by pages that scroll themselves). */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    AppTopBar(
        leading = {
            AppBackButton(onBack)
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        },
    )
}

/** Collects the rows of one group so each can be given the right corner shape. */
class SettingsGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        rows += content
    }
}

private val OuterCorner = 24.dp
private val InnerCorner = 5.dp

/** A titled group of connected cards: big corners at the ends, small ones between rows, a thin gap in between. */
@Composable
fun SettingsGroup(title: String? = null, note: String? = null, content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    val outline = if (LocalPureMode.current.isOn) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEachIndexed { i, row ->
                val top = if (i == 0) OuterCorner else InnerCorner
                val bottom = if (i == rows.lastIndex) OuterCorner else InnerCorner
                Surface(
                    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = outline,
                    modifier = Modifier.fillMaxWidth(),
                ) { row() }
            }
        }
        if (note != null) SettingsNote(note, Modifier.padding(top = 8.dp))
    }
}

/** A small explanation under a group (or anywhere on the page). */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun RowText(title: String, note: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier.alpha(if (enabled) 1f else 0.5f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val RowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

/** Title (+ optional note) on the left, switch on the right; the whole row toggles. */
@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, note: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowText(title, note, enabled, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** One choice of a group of options (radio on the left). */
@Composable
fun SettingsRadioRow(title: String, selected: Boolean, onClick: () -> Unit, note: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        RowText(title, note, enabled, Modifier.weight(1f))
    }
}

/** A row that opens something (a page or a dialog): title, current value underneath, optional widget on the right. */
@Composable
fun SettingsLinkRow(title: String, onClick: () -> Unit, summary: String? = null, enabled: Boolean = true, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowText(title, summary, enabled, Modifier.weight(1f))
        trailing()
    }
}

/** A slider with its title and current value on one line, an optional note, then the slider. */
@Composable
fun SettingsSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    note: String? = null,
) {
    Column(Modifier.fillMaxWidth().then(RowPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/** Free content (a picture, a preview, a button) inside a card with the standard padding. */
@Composable
fun SettingsCustomRow(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().then(RowPadding), content = content)
}

/** A row of choice chips in the settings style: filled, no outline; the chosen one takes the accent container. */
@Composable
fun SettingsChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            androidx.compose.material3.FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(label, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal) },
                shape = RoundedCornerShape(50),
                border = null,
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

/** A filled text field with a soft background and no underline, matching the settings cards. */
@Composable
fun FilledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        label = { Text(label) },
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * A thick, rounded progress bar with a little volume (a soft highlight on the upper half). The fill moves smoothly towards
 * each new value; with [progress] null (nothing reported yet) a segment sweeps along instead, so it never looks stuck at 0.
 */
@Composable
fun ChunkyProgress(progress: Float?, modifier: Modifier = Modifier, thickness: androidx.compose.ui.unit.Dp = 14.dp) {
    val shape = RoundedCornerShape(50)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    Box(modifier.fillMaxWidth().height(thickness).clip(shape).background(track)) {
        if (progress == null) {
            val sweep by rememberInfiniteTransition(label = "dlSweep").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                label = "dlSweepX",
            )
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width * 0.3f
                    val x = (size.width + w) * sweep - w
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(start, end), startX = x, endX = x + w),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            )
        } else {
            val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = LinearOutSlowInEasing), label = "dlProgress")
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(shown.coerceAtLeast(0.04f)).clip(shape)
                    .background(Brush.horizontalGradient(listOf(start, end))),
            ) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))))
            }
        }
    }
}
