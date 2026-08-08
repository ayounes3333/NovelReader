package my.noveldokusha.tooling.local_server_sync.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import my.noveldokusha.tooling.local_server_sync.auth.AuthState

@Composable
fun LocalServerSyncPreferencesSection(
    modifier: Modifier = Modifier,
    viewModel: LocalServerSyncViewModel = hiltViewModel()
) {
    var showAuthDialog by remember { mutableStateOf(false) }
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var showAddBssidDialog by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val serverUrls by viewModel.serverUrls.collectAsState()
    val homeBssids by viewModel.homeBssids.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Server Sync",
                    style = MaterialTheme.typography.titleMedium
                )

                // Connection status indicator
                when (authState) {
                    is AuthState.Authenticated -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AuthState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "Not connected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Connection info
            when (authState) {
                is AuthState.Authenticated -> {
                    Text(
                        text = "Connected as: ${(authState as AuthState.Authenticated).username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Email: ${(authState as AuthState.Authenticated).email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is AuthState.Loading -> {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Not connected to local server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sync status
            when (syncState) {
                is SyncState.Syncing -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Syncing: ${(syncState as SyncState.Syncing).operation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is SyncState.Success -> {
                    Text(
                        text = "Last sync: ${(syncState as SyncState.Success).timestamp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SyncState.Error -> {
                    Text(
                        text = "Sync error: ${(syncState as SyncState.Error).message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    // No sync status to show
                }
            }

            // Auto-sync toggle (only when authenticated)
            if (authState is AuthState.Authenticated) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-sync on WiFi",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Automatically sync when connected to a network with the server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                    )
                }

                // Server URLs section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Server addresses",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { showAddUrlDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add server URL"
                        )
                    }
                }

                if (serverUrls.isEmpty()) {
                    Text(
                        text = "No server addresses configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    serverUrls.forEach { url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.removeServerUrl(url) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Home WiFi BSSIDs (gate for bulk sync)
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home WiFi networks",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (homeBssids.isEmpty())
                                "Any WiFi will trigger bulk sync. Add the current network to restrict."
                            else
                                "Bulk sync runs only when connected to a listed BSSID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAddBssidDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add home WiFi"
                        )
                    }
                }

                homeBssids.forEach { bssid ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bssid,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeHomeBssid(bssid) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (authState) {
                    is AuthState.Authenticated -> {
                        Button(
                            onClick = { viewModel.syncLibrary() },
                            enabled = syncState !is SyncState.Syncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync")
                        }

                        OutlinedButton(
                            onClick = { viewModel.syncComplete() },
                            enabled = syncState !is SyncState.Syncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Full Sync")
                        }

                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            enabled = syncState !is SyncState.Syncing
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = { showAuthDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }

    // Show auth dialog when needed
    if (showAuthDialog) {
        LocalServerAuthDialog(
            onDismiss = { showAuthDialog = false }
        )
    }

    // Add server URL dialog
    if (showAddUrlDialog) {
        var urlInput by remember { mutableStateOf("http://") }
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            title = { Text("Add Server Address") },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addServerUrl(urlInput)
                        showAddUrlDialog = false
                    },
                    enabled = urlInput.startsWith("http")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add home WiFi BSSID dialog
    if (showAddBssidDialog) {
        val ctx = LocalContext.current
        var hasLocationPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        var detected by remember(hasLocationPermission) {
            mutableStateOf(if (hasLocationPermission) viewModel.getCurrentBssid() else null)
        }
        var bssidInput by remember { mutableStateOf(detected.orEmpty()) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasLocationPermission = granted
            if (granted) {
                val current = viewModel.getCurrentBssid()
                detected = current
                if (!current.isNullOrBlank()) bssidInput = current
            }
        }

        AlertDialog(
            onDismissRequest = { showAddBssidDialog = false },
            title = { Text("Add Home WiFi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val helper = when {
                        !hasLocationPermission ->
                            "Location permission is required to read the current WiFi BSSID. Grant it, or paste the BSSID manually (aa:bb:cc:dd:ee:ff)."
                        detected != null ->
                            "Detected current WiFi BSSID. Tap Add to mark this network as home."
                        else ->
                            "Could not read BSSID. Make sure WiFi is on, or paste it manually (aa:bb:cc:dd:ee:ff)."
                    }
                    Text(text = helper, style = MaterialTheme.typography.bodySmall)

                    if (!hasLocationPermission) {
                        TextButton(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        ) { Text("Grant location permission") }
                    }

                    OutlinedTextField(
                        value = bssidInput,
                        onValueChange = { bssidInput = it.lowercase() },
                        label = { Text("BSSID") },
                        placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addHomeBssid(bssidInput)
                        showAddBssidDialog = false
                    },
                    enabled = bssidInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBssidDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
