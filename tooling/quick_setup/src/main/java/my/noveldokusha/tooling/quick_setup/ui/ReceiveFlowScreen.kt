package my.noveldokusha.tooling.quick_setup.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldoksuha.coreui.theme.AppSpacing
import my.noveldokusha.tooling.quick_setup.discovery.NsdDiscoverer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveFlowScreen(
    viewModel: QuickSetupViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
) {
    val state by viewModel.receiveState.collectAsState()
    val context = LocalContext.current

    var showQrScanner by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf<List<NsdDiscoverer.DiscoveredDevice>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(false) }
    // Prefill from an interrupted session so Resume works after an app restart
    // (the token must be re-entered/re-scanned as it is not persisted).
    val resumableEndpoint = remember { viewModel.resumableSessionEndpoint }
    var host by remember { mutableStateOf(resumableEndpoint?.first ?: "") }
    var port by remember { mutableStateOf(resumableEndpoint?.second?.takeIf { it > 0 }?.toString() ?: "8765") }
    var token by remember { mutableStateOf("") }

    val nsdDiscoverer = remember { NsdDiscoverer(context) }
    val hasResumableSession = viewModel.hasResumableSession

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showQrScanner = true
    }

    DisposableEffect(Unit) {
        onDispose { nsdDiscoverer.stopDiscovery() }
    }

    if (showQrScanner) {
        QrCodeScanner(
            onScanned = { qrContent ->
                val info = my.noveldokusha.tooling.quick_setup.pairing.PairingManager.parseQrPayload(qrContent)
                if (info != null) {
                    host = info.ip
                    port = info.port.toString()
                    token = info.sessionToken
                    showQrScanner = false
                    viewModel.startReceive(info.ip, info.port, info.sessionToken)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Receive from Old Device") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is QuickSetupViewModel.ReceiveState.Idle -> {
                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    if (hasResumableSession) {
                        Text(
                            text = "A previous transfer was interrupted. Re-scan the QR code or enter the session token below, then resume.",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.resumeReceive(host, port.ifBlank { "8765" }.toInt(), token) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = host.isNotBlank() && token.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text("  Resume Transfer", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    FilledTonalButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) showQrScanner = true
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Text("  Scan QR Code", modifier = Modifier.padding(start = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = {
                            if (isDiscovering) {
                                nsdDiscoverer.stopDiscovery()
                                isDiscovering = false
                            } else {
                                discoveredDevices = emptyList()
                                nsdDiscoverer.startDiscovery { discoveryState ->
                                    when (discoveryState) {
                                        is NsdDiscoverer.DiscoveryState.DeviceFound -> {
                                            if (discoveredDevices.none { it.name == discoveryState.device.name }) {
                                                discoveredDevices = discoveredDevices + discoveryState.device
                                            }
                                        }
                                        is NsdDiscoverer.DiscoveryState.DeviceLost -> {
                                            discoveredDevices = discoveredDevices.filter { it.name != discoveryState.deviceName }
                                        }
                                        is NsdDiscoverer.DiscoveryState.Discovering -> { isDiscovering = true }
                                        is NsdDiscoverer.DiscoveryState.Error -> { isDiscovering = false }
                                        else -> {}
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                        Text(
                            text = if (isDiscovering) "Stop Scanning" else "Scan Network",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    if (discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Found Devices:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            items(discoveredDevices) { device ->
                                ListItem(
                                    headlineContent = { Text(device.deviceName) },
                                    supportingContent = { Text("${device.host}:${device.port}") },
                                    modifier = Modifier.clickable {
                                        // Fill in host/port, user must enter token manually
                                        host = device.host
                                        port = device.port.toString()
                                        nsdDiscoverer.stopDiscovery()
                                        isDiscovering = false
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Or enter connection details manually:", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Session Token") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(
                        onClick = { viewModel.startReceive(host, port.toIntOrNull() ?: 8765, token) },
                        enabled = host.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect & Transfer")
                    }
                }

                is QuickSetupViewModel.ReceiveState.Resuming -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text("Resuming previous transfer...", style = MaterialTheme.typography.titleMedium)
                }

                is QuickSetupViewModel.ReceiveState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text("Connecting to ${s.host}:${s.port}...", style = MaterialTheme.typography.titleMedium)
                }

                is QuickSetupViewModel.ReceiveState.InProgress -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { s.progress.percentComplete },
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.large))
                        Text(s.progress.phase.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${s.progress.itemsTransferred} / ${s.progress.totalItems}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.progress.totalBytes > 0) {
                            Text(
                                text = "${s.progress.bytesTransferred / 1024} / ${s.progress.totalBytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.large))
                        OutlinedButton(onClick = { viewModel.cancelTransfer() }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Text("  Cancel", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                is QuickSetupViewModel.ReceiveState.Completed -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text("Transfer Complete!", style = MaterialTheme.typography.headlineMedium)

                    val summary = s.summary
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Books: ${summary.booksTransferred} / ${summary.manifest.booksCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Chapters: ${summary.chaptersTransferred} / ${summary.manifest.chaptersCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Images: ${summary.imagesTransferred} / ${summary.manifest.imagesCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Settings: ${if (summary.preferencesTransferred) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Duration: ${summary.durationSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (summary.wasResumed) {
                        Text(
                            text = "(Resumed from previous session)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (summary.errors.isNotEmpty()) {
                        Text(
                            text = "${summary.errors.size} errors occurred",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    FilledTonalButton(onClick = {
                        viewModel.resetReceiveState()
                        viewModel.clearSession()
                        onDone()
                    }) {
                        Text("Done")
                    }
                }

                is QuickSetupViewModel.ReceiveState.Error -> {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Text("Transfer Failed", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(onClick = { viewModel.resetReceiveState() }) {
                            Text("Try Again")
                        }
                        if (s.summary != null && !s.summary.isFullyComplete) {
                            OutlinedButton(onClick = {
                                viewModel.resumeReceive(host, port.toIntOrNull() ?: 8765, token)
                            }) {
                                Text("Resume")
                            }
                        }
                    }
                }
            }
        }
    }
}
