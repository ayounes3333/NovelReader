package my.noveldokusha.tooling.quick_setup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldoksuha.coreui.theme.AppSpacing
import my.noveldokusha.tooling.quick_setup.usb.UsbConnectionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbConnectionScreen(
    viewModel: UsbConnectionViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val transferState by viewModel.transferState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("USB Transfer") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val cs = connectionState) {
                is UsbConnectionManager.ConnectionState.Disconnected -> {
                    Icon(
                        Icons.Filled.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Connect a USB-C cable between both devices",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No pairing code needed - the physical cable is your consent.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(
                        onClick = { viewModel.startConnection() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Detect USB Connection")
                    }
                }

                is UsbConnectionManager.ConnectionState.DetectingRole -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Detecting USB role...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Determining which device is host and which is accessory.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                is UsbConnectionManager.ConnectionState.RequestingPermission -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Requesting USB permission...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                is UsbConnectionManager.ConnectionState.WaitingForReenumeration -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Waiting for device to switch...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "The device is switching to accessory mode. This may take a few seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                is UsbConnectionManager.ConnectionState.Connected -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "USB Connected!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Role: ${cs.role.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    when (val ts = transferState) {
                        is UsbConnectionViewModel.TransferState.Idle -> {
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.startSendViaUsb() },
                                ) {
                                    Text("Send Data")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.startReceiveViaUsb() },
                                ) {
                                    Text("Receive Data")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Text("Disconnect")
                            }
                        }

                        is UsbConnectionViewModel.TransferState.Sending,
                        is UsbConnectionViewModel.TransferState.Receiving -> {
                            val progress = when (ts) {
                                is UsbConnectionViewModel.TransferState.Sending -> ts.progress
                                is UsbConnectionViewModel.TransferState.Receiving -> ts.progress
                                else -> return@Scaffold
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            CircularProgressIndicator(
                                progress = { progress.percentComplete },
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = progress.phase.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${progress.itemsTransferred} / ${progress.totalItems}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        is UsbConnectionViewModel.TransferState.Completed -> {
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Transfer Complete!",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            FilledTonalButton(onClick = {
                                viewModel.disconnect()
                                onDone()
                            }) {
                                Text("Done")
                            }
                        }

                        is UsbConnectionViewModel.TransferState.Error -> {
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = ts.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.large))
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Text("Try Again")
                            }
                        }
                    }
                }

                is UsbConnectionManager.ConnectionState.Error -> {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = cs.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = { viewModel.startConnection() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
