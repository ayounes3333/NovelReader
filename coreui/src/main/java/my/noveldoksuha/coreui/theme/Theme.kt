package my.noveldoksuha.coreui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController


@Composable
fun Theme(
    themeProvider: ThemeProvider,
    content: @Composable () -> @Composable Unit,
) {
    val followSystemsTheme by themeProvider.followSystem(rememberCoroutineScope())
    val selectedTheme by themeProvider.currentTheme(rememberCoroutineScope())
    val accent by themeProvider.accentColor(rememberCoroutineScope())

    val isSystemThemeLight = !isSystemInDarkTheme()
    val theme: Themes = when (followSystemsTheme) {
        true -> when {
            isSystemThemeLight && !selectedTheme.isLight -> Themes.LIGHT
            !isSystemThemeLight && selectedTheme.isLight -> Themes.DARK
            else -> selectedTheme
        }
        false -> selectedTheme
    }
    InternalTheme(
        theme = theme,
        accent = accent,
        content = content,
    )
}

@Composable
fun InternalTheme(
    theme: Themes = if (isSystemInDarkTheme()) Themes.DARK else Themes.LIGHT,
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit
) {
    val palette = remember(accent) { AccentPalette(accent) }
    val colorScheme = remember(theme, palette) {
        when (theme) {
            Themes.LIGHT -> lightColorScheme(palette)
            Themes.DARK -> darkColorScheme(palette)
            Themes.BLACK -> blackColorScheme(palette)
        }
    }

    val appColor = remember(theme, palette) {
        when (theme) {
            Themes.LIGHT -> lightAppColor(palette)
            Themes.DARK -> darkAppColor(palette)
            Themes.BLACK -> blackAppColor(palette)
        }
    }

    val systemUiController = rememberSystemUiController()
    systemUiController.setSystemBarsColor(
        color = colorScheme.primary,
        darkIcons = theme.isLight
    )
    val textSelectionColors = remember(palette) {
        TextSelectionColors(
            handleColor = palette.accent,
            backgroundColor = palette.accent.copy(alpha = 0.3f)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onPrimary,
            LocalAppColor provides appColor,
            LocalTextSelectionColors provides textSelectionColors,
            content = content
        )
    }
}
