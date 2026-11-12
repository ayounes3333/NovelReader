package my.noveldokusha.settings.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.components.SettingsSectionTitle
import my.noveldoksuha.coreui.theme.AccentPalette
import my.noveldoksuha.coreui.theme.AccentPresets
import my.noveldoksuha.coreui.theme.Themes
import my.noveldokusha.settings.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsTheme(
    currentTheme: Themes,
    currentFollowSystem: Boolean,
    currentAccentColor: Int,
    onFollowSystemChange: (Boolean) -> Unit,
    onCurrentThemeChange: (Themes) -> Unit,
    onAccentColorChange: (Int) -> Unit,
) {
    Column {
        SettingsSectionTitle(text = stringResource(id = R.string.theme))
        // Follow system theme
        ListItem(
            modifier = Modifier
                .clickable { onFollowSystemChange(!currentFollowSystem) },
            headlineContent = {
                Text(text = stringResource(id = R.string.follow_system))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            },
            trailingContent = {
                Switch(
                    checked = currentFollowSystem,
                    onCheckedChange = onFollowSystemChange
                )
            }
        )
        // Themes
        ListItem(
            headlineContent = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Themes.entries.forEach {
                        FilterChip(
                            selected = it == currentTheme,
                            onClick = { onCurrentThemeChange(it) },
                            label = { Text(text = stringResource(id = it.nameId)) }
                        )
                    }
                }

            },
            leadingContent = {
                Icon(Icons.Outlined.ColorLens, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        )
        // Accent color
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = R.string.theme_accent_color))
            },
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    AccentPresets.forEach { color ->
                        AccentSwatch(
                            color = color,
                            selected = color.toArgb() == currentAccentColor,
                            onClick = { onAccentColorChange(color.toArgb()) }
                        )
                    }
                }
            },
            leadingContent = {
                Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        )
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(targetValue = if (selected) 3.dp else 0.dp, label = "")
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
        label = ""
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = AccentPalette(color).onAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
