package my.noveldoksuha.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aliyounes.aurui.components.AurTextField
import my.noveldoksuha.coreui.theme.InternalTheme

@Composable
fun MyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeHolderText: String,
    modifier: Modifier = Modifier
) {
    AurTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeHolderText,
        modifier = modifier
    )
}

@Preview
@Composable
private fun PreviewView() {
    InternalTheme {
        MyOutlinedTextField(
            value = "",
            onValueChange = {},
            placeHolderText = "placeholder"
        )
    }
}