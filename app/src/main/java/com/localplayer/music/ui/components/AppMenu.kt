package com.localplayer.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.localplayer.music.ui.theme.LocalPureMode

/**
 * A pop-up menu in the same style as the settings cards: big rounded corners, the card surface colour, no tint, and a
 * thin outline in pure mode. Entries are [AppMenuItem]s.
 */
@Composable
fun AppDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val outline = if (LocalPureMode.current.isOn) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = outline,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content()
        }
    }
}

/**
 * One entry of an [AppDropdownMenu]: the icon in a soft round chip, then the label. Without an [icon] the row is
 * text only; a [selected] row is tinted and bold (used for the sort menu).
 */
@Composable
fun AppMenuItem(icon: ImageVector?, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (icon == null) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Surface(shape = CircleShape, color = scheme.primaryContainer.copy(alpha = 0.6f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.padding(start = if (icon == null) 8.dp else 0.dp, end = 12.dp),
        )
    }
}
