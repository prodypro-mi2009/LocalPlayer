package com.localplayer.music.ui.components

import com.localplayer.music.ui.theme.onColorFor
import com.localplayer.music.ui.theme.controlColor
import com.localplayer.music.data.personalization.ControlColorGroup
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
import com.localplayer.music.ui.theme.LocalReduceMotion
import com.localplayer.music.ui.theme.Motion
import com.localplayer.music.ui.theme.pressScale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The top-level library destinations shown in the bottom navigation.
 * Years is intentionally NOT here: its data and route still exist, but it is not part of the visible UI.
 */
enum class LibraryTab(@param:StringRes val label: Int) {
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
 * A bright top edge fading out by 55 % of the height, like glass catching light. Used as a 1 dp border on the
 * floating pieces (search button, tab pill, mini player). White at alpha 0 (not Color.Transparent, which is
 * transparent BLACK and would darken the fade).
 */
internal val FloatingEdgeHighlight: Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.20f),
    0.55f to Color.White.copy(alpha = 0f),
)

/** Position and size of one tab inside the scrolling tab row (before any press scale is applied). */
internal data class TabBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Bottom navigation: a round Search button on the left and a pill of tabs. The pill scrolls
 * horizontally when the screen is too narrow for all six labels, and keeps the selected tab centred.
 * The selection is ONE capsule that slides (and resizes) to the selected tab with a critically damped spring.
 */
@Composable
fun LibraryBottomNav(
    selected: LibraryTab,
    order: List<LibraryTab>,
    onSelect: (LibraryTab) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val searchLabel = stringResource(R.string.search)
        val searchSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(52.dp)
                .pressScale(searchSource)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = BAR_SURFACE_ALPHA))
                .border(1.dp, FloatingEdgeHighlight, CircleShape)
                .clickable(
                    interactionSource = searchSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onSearch,
                )
                .semantics { contentDescription = searchLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }

        val scroll = rememberScrollState()
        val density = LocalDensity.current

        // Every tab reports its bounds; an unchanged value is not stored again, so scrolling never restarts effects.
        val bounds = remember { mutableStateMapOf<LibraryTab, TabBounds>() }
        val selectedBounds = bounds[selected]

        val custom = controlColor(ControlColorGroup.NAVIGATION, MaterialTheme.colorScheme.surfaceContainer)
        val capsuleColor = custom ?: MaterialTheme.colorScheme.primaryContainer
        val selectedContent = if (custom != null) onColorFor(custom) else MaterialTheme.colorScheme.onPrimaryContainer
        val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant

        // The capsule: x and width are springs; the very first placement and reduced motion jump instead.
        val capsuleX = remember { Animatable(0f) }
        val capsuleWidth = remember { Animatable(0f) }
        val placed = remember { booleanArrayOf(false) }
        LaunchedEffect(selectedBounds, reduceMotion) {
            val b = selectedBounds ?: return@LaunchedEffect
            if (!placed[0] || reduceMotion) {
                placed[0] = true
                capsuleX.snapTo(b.x)
                capsuleWidth.snapTo(b.width)
            } else {
                launch { capsuleX.animateTo(b.x, Motion.SettleCritical) }
                launch { capsuleWidth.animateTo(b.width, Motion.SettleCritical) }
            }
        }

        // Keep the selected tab centred in the visible part of the pill (instant under reduced motion).
        var viewportPx by remember { mutableIntStateOf(0) }
        val scrolledOnce = remember { booleanArrayOf(false) }
        LaunchedEffect(selectedBounds, viewportPx, reduceMotion) {
            val b = selectedBounds ?: return@LaunchedEffect
            if (viewportPx <= 0) return@LaunchedEffect
            val paddingPx = with(density) { 4.dp.toPx() }
            val target = (b.x + b.width / 2f + paddingPx - viewportPx / 2f).roundToInt().coerceAtLeast(0)
            if (reduceMotion || !scrolledOnce[0]) {
                scrolledOnce[0] = true
                scroll.scrollTo(target)
            } else {
                scroll.animateScrollTo(target)
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = BAR_SURFACE_ALPHA))
                .border(1.dp, FloatingEdgeHighlight, CircleShape)
                .onSizeChanged { viewportPx = it.width }
                .horizontalScroll(scroll)
                .padding(4.dp)
                // After the scroll + padding, so the capsule scrolls together with the tabs.
                .drawBehind {
                    val b = selectedBounds ?: return@drawBehind
                    val width = capsuleWidth.value
                    if (width <= 0f) return@drawBehind
                    drawRoundRect(
                        color = capsuleColor,
                        topLeft = Offset(capsuleX.value, b.y),
                        size = Size(width, b.height),
                        cornerRadius = CornerRadius(b.height / 2f),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            order.forEach { tab ->
                PillTab(
                    label = stringResource(tab.label),
                    selected = tab == selected,
                    selectedContent = selectedContent,
                    unselectedContent = unselectedContent,
                    onClick = { onSelect(tab) },
                    onBounds = { b -> if (bounds[tab] != b) bounds[tab] = b },
                )
            }
        }
    }
}

/** One tab: it animates ONLY its text colour; the selection background is the sliding capsule behind it. */
@Composable
private fun PillTab(
    label: String,
    selected: Boolean,
    selectedContent: Color,
    unselectedContent: Color,
    onClick: () -> Unit,
    onBounds: (TabBounds) -> Unit,
) {
    val content by animateColorAsState(
        if (selected) selectedContent else unselectedContent,
        Motion.ColorChange,
        label = "pillContent",
    )
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // Bounds are reported BEFORE the press scale, so the capsule is not affected by it.
            .onGloballyPositioned { c ->
                val p = c.positionInParent()
                onBounds(TabBounds(p.x, p.y, c.size.width.toFloat(), c.size.height.toFloat()))
            }
            .pressScale(source, pressedScale = 0.96f)
            .heightIn(min = TouchTarget)
            .widthIn(min = TouchTarget)
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
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
