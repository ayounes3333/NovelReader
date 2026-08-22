package my.noveldokusha.tooling.quick_setup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldoksuha.coreui.theme.AppSpacing
import my.noveldokusha.tooling.quick_setup.pairing.PairingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFlowScreen(
    viewModel: QuickSetupViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
) {
    val state by viewModel.sendState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Send to New Device") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                is QuickSetupViewModel.SendState.Idle -> {
                    Icon(
                        Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Send your library to a new device",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start the server and scan the QR code on your new device.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = { viewModel.startSendServer() }) {
                        Text("Start Server")
                    }
                }

                is QuickSetupViewModel.SendState.ServerRunning -> {
                    Text(
                        text = "Scan this QR code on your new device",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val qrContent = remember(s) {
                        PairingManager.createQrPayload(
                            ip = s.hostIp ?: "",
                            port = s.port,
                            sessionToken = s.sessionToken,
                            deviceName = s.hostName ?: "Unknown Device",
                        )
                    }
                    QrCodeImage(
                        content = qrContent,
                        size = 220,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Or enter manually:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Host: ${s.hostIp ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Port: ${s.port}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Token: ${s.sessionToken.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = { viewModel.stopSendServer() }) {
                        Text("Stop Server")
                    }
                }

                is QuickSetupViewModel.SendState.WaitingForAccept -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Device connecting...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${s.deviceName} wants to connect",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(onClick = { viewModel.acceptConnection() }) {
                            Text("Accept")
                        }
                        OutlinedButton(onClick = { viewModel.rejectConnection() }) {
                            Text("Reject")
                        }
                    }
                }

                is QuickSetupViewModel.SendState.InProgress -> {
                    CircularProgressIndicator(
                        progress = { s.progress.percentComplete },
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = s.progress.phase.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${s.progress.itemsTransferred} / ${s.progress.totalItems}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is QuickSetupViewModel.SendState.Completed -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Transfer Complete!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = {
                        viewModel.resetSendState()
                        onDone()
                    }) {
                        Text("Done")
                    }
                }

                is QuickSetupViewModel.SendState.Error -> {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text(
                        text = "Transfer Failed",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = { viewModel.resetSendState() }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
