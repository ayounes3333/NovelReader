package my.noveldoksuha.coreui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import my.noveldoksuha.coreui.theme.AppSpacing
import my.noveldoksuha.coreui.theme.InternalTheme

/**
 * Design-system search input used below top app bars.
 * Standardized shape, paddings and icon handling.
 */
@Composable
fun AppSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Filled.Search,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(text = placeholder) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = trailingIcon?.let { icon ->
            {
                IconButton(onClick = { onTrailingIconClick?.invoke() ?: onValueChange("") }) {
                    Icon(icon, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
    )
}

@Preview
@Composable
private fun PreviewView() {
    InternalTheme {
        AppSearchTextField(
            value = "",
            onValueChange = {},
            placeholder = "Search by title",
        )
    }
}
