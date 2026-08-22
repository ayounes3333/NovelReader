package my.noveldoksuha.coreui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import my.noveldoksuha.coreui.theme.InternalTheme

@Composable
fun MyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeHolderText: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(text = placeHolderText) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
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