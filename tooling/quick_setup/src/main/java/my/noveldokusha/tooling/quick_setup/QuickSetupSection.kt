package my.noveldokusha.tooling.quick_setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.textPadding

@Composable
fun QuickSetupSection(
    onSendViaFile: () -> Unit,
    onSendViaWifi: () -> Unit,
    onSendViaUsb: () -> Unit,
    onReceiveViaFile: () -> Unit,
    onReceiveViaWifi: () -> Unit,
    onReceiveViaUsb: () -> Unit,
) {
    Column {
        Text(
            text = "Quick Setup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = ColorAccent
        )
        Text(
            text = "Send to new device",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textPadding(),
        )
        ListItem(
            headlineContent = { Text("Via USB cable (fastest)") },
            supportingContent = {
                Text("Direct device-to-device transfer via USB cable. No network required.")
            },
            leadingContent = {
                Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onSendViaUsb() }
        )
        ListItem(
            headlineContent = { Text("Via Wi-Fi (recommended)") },
            supportingContent = {
                Text("Transfer over the same network. Both devices must be on the same Wi-Fi.")
            },
            leadingContent = {
                Icon(Icons.Outlined.CloudUpload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onSendViaWifi() }
        )
        ListItem(
            headlineContent = { Text("Via backup file") },
            supportingContent = {
                Text("Export to a file you can transfer via USB, SD card, or cloud storage.")
            },
            leadingContent = {
                Icon(Icons.Outlined.FileUpload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onSendViaFile() }
        )
        Text(
            text = "Receive from old device",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textPadding(),
        )
        ListItem(
            headlineContent = { Text("Via USB cable (fastest)") },
            supportingContent = {
                Text("Connect a USB cable. The app auto-detects the connection.")
            },
            leadingContent = {
                Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onReceiveViaUsb() }
        )
        ListItem(
            headlineContent = { Text("Via Wi-Fi (recommended)") },
            supportingContent = {
                Text("Connect to your old device over the same network.")
            },
            leadingContent = {
                Icon(Icons.Outlined.CloudDownload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onReceiveViaWifi() }
        )
        ListItem(
            headlineContent = { Text("From backup file") },
            supportingContent = {
                Text("Import from a backup file transferred from your old device.")
            },
            leadingContent = {
                Icon(Icons.Outlined.FileDownload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onReceiveViaFile() }
        )
    }
}
