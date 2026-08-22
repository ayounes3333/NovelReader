package my.noveldoksuha.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import my.noveldoksuha.coreui.theme.AppSpacing
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorAccentContent
import my.noveldoksuha.coreui.theme.ImageBorderShape
import my.noveldoksuha.coreui.theme.InternalTheme

/**
 * Design-system badge overlaid on book covers (unread count, group count, "local" tag).
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorAccent,
    contentColor: Color = ColorAccentContent,
) {
    Text(
        text = text,
        color = contentColor,
        modifier = modifier
            .padding(AppSpacing.small)
            .background(backgroundColor, ImageBorderShape)
            .padding(AppSpacing.xsmall)
    )
}

@Preview
@Composable
private fun PreviewView() {
    InternalTheme {
        AppBadge(text = "12")
    }
}
