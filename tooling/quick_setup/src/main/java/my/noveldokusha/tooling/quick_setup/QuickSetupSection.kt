package my.noveldokusha.tooling.quick_setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.components.SettingsSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSetupSection(
    onSendViaFile: () -> Unit,
    onSendViaWifi: () -> Unit,
    onSendViaUsb: () -> Unit,
    onReceiveViaFile: () -> Unit,
    onReceiveViaWifi: () -> Unit,
    onReceiveViaUsb: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Send to new device", "Receive from old device")

    Column {
        SettingsSectionTitle(text = "Quick Setup")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selectedTab == index) {
                            Icon(
                                imageVector = if (index == 0) Icons.Outlined.CloudUpload else Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                    },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "quickSetupTab"
        ) { tab ->
            Column {
                if (tab == 0) {
                    QuickSetupRow(
                        icon = Icons.Outlined.Usb,
                        title = "Via USB cable (fastest)",
                        description = "Direct device-to-device transfer via USB cable. No network required.",
                        onClick = onSendViaUsb
                    )
                    QuickSetupRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = "Via Wi-Fi (recommended)",
                        description = "Transfer over the same network. Both devices must be on the same Wi-Fi.",
                        onClick = onSendViaWifi
                    )
                    QuickSetupRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Via backup file",
                        description = "Export to a file you can transfer via USB, SD card, or cloud storage.",
                        onClick = onSendViaFile
                    )
                } else {
                    QuickSetupRow(
                        icon = Icons.Outlined.Usb,
                        title = "Via USB cable (fastest)",
                        description = "Connect a USB cable. The app auto-detects the connection.",
                        onClick = onReceiveViaUsb
                    )
                    QuickSetupRow(
                        icon = Icons.Outlined.CloudDownload,
                        title = "Via Wi-Fi (recommended)",
                        description = "Connect to your old device over the same network.",
                        onClick = onReceiveViaWifi
                    )
                    QuickSetupRow(
                        icon = Icons.Outlined.FileDownload,
                        title = "From backup file",
                        description = "Import from a backup file transferred from your old device.",
                        onClick = onReceiveViaFile
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSetupRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
        },
        modifier = Modifier.clickable { onClick() }
    )
}
