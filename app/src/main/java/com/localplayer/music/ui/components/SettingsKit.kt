package com.localplayer.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.localplayer.music.R
import com.localplayer.music.ui.theme.LocalPureMode

/**
 * Shared building blocks for the settings pages, so every page looks and behaves the same:
 * a large page title with a round back button, groups of connected rounded cards (like the main Settings list),
 * and a handful of row types (switch, choice, link, slider, note). Colours come from the Material theme only.
 */

/** A settings page: round back button + big left-aligned title, then a scrollable column of groups. */
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(title, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            content()
            Spacer(Modifier.height(4.dp + LocalBottomBarInset.current))
        }
    }
}

/** The page title bar: round back button + big left-aligned title (also used by pages that scroll themselves). */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    AppTopBar(
        leading = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)) {
                IconButton(onClick = onBack, modifier = Modifier.size(TopBarMetrics.ButtonSize)) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
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
