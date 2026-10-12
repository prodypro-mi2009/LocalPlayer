package com.localplayer.music.ui.components

import com.localplayer.music.ui.theme.onColorFor
import com.localplayer.music.ui.theme.controlColor
import com.localplayer.music.data.personalization.ControlColorGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localplayer.music.R
import com.localplayer.music.ui.BAR_SURFACE_ALPHA

/**
 * The top-level library destinations shown in the bottom navigation.
 * Years is intentionally NOT here: its data and route still exist, but it is not part of the visible UI.
 */
enum class LibraryTab(@StringRes val label: Int) {
    HOME(R.string.tab_home),
    SONGS(R.string.tab_songs),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    PLAYLISTS(R.string.tab_playlists),
    GENRES(R.string.tab_genres),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): LibraryTab = entries.getOrElse(ordinal) { HOME }
    }
}

/** Minimum size of every touch target in the shell (Material accessibility guideline). */
private val TouchTarget = 48.dp

/**
 * Bottom navigation: a round Search button on the left and a pill of tabs. The pill scrolls
 * horizontally when the screen is too narrow for all six labels, and keeps the selected tab in view.
 */
@Composable
fun LibraryBottomNav(
    selected: LibraryTab,
    order: List<LibraryTab>,
    onSelect: (LibraryTab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val searchLabel = stringResource(R.string.search)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = BAR_SURFACE_ALPHA))
                .clickable(role = Role.Button, onClick = onSearch)
                .semantics { contentDescription = searchLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }

        val scroll = rememberScrollState()
        val density = LocalDensity.current
        // Keep the selected tab visible on narrow screens (approximate position: tabs are similar in width).
        LaunchedEffect(selected) {
            val target = with(density) { (order.indexOf(selected).coerceAtLeast(0) * 76).dp.roundToPx() }
            scroll.animateScrollTo((target - with(density) { 48.dp.roundToPx() }).coerceAtLeast(0))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = BAR_SURFACE_ALPHA))
                .horizontalScroll(scroll)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            order.forEach { tab ->
                PillTab(
                    label = stringResource(tab.label),
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val custom = controlColor(ControlColorGroup.NAVIGATION, MaterialTheme.colorScheme.surfaceContainer)
    val container = if (selected) custom ?: MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val content = if (selected) {
        if (custom != null) onColorFor(custom) else MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .heightIn(min = TouchTarget)
            .widthIn(min = TouchTarget)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Geometry shared by EVERY top bar, so the Settings button is at exactly the same place on all screens. */
object TopBarMetrics {
    /** Row height. The 48dp buttons are vertically centred in it. */
    val Height = 56.dp

    /** Space between the screen edge and the first/last button. */
    val HorizontalPadding = 8.dp

    /** Touch target of every button in the bar. */
    val ButtonSize = 48.dp
}

/**
 * THE top bar of the app (main tabs and detail screens both use it): status-bar inset, then a fixed-height
 * row with fixed side padding; [leading] fills the free space on the left, [trailing] sits at the right edge.
 * Because height, padding and button size are constants, the trailing Settings button cannot drift between screens.
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarMetrics.Height)
            .padding(horizontal = TopBarMetrics.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        trailing()
    }
}

/** Compact top controls of the main tabs (no title): empty leading slot and the Settings button. */
@Composable
fun TopControls(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
) {
    AppTopBar(
        modifier = modifier,
        leading = { Box(Modifier.weight(1f)) { leading() } },
        trailing = { SettingsButton(onSettings) },
    )
}

/** The global Settings button (48dp touch target). */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(TopBarMetrics.ButtonSize)) {
        Icon(AppIcons.Settings, contentDescription = stringResource(R.string.open_settings_cd))
    }
}
