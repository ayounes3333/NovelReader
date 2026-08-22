package my.noveldokusha.tooling.quick_setup.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.quick_setup.client.QuickSetupClient
import my.noveldokusha.tooling.quick_setup.discovery.NsdAdvertiser
import my.noveldokusha.tooling.quick_setup.pairing.PairingManager
import my.noveldokusha.tooling.quick_setup.server.QuickSetupAcceptGate
import my.noveldokusha.tooling.quick_setup.server.QuickSetupServerService
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupExporter
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupImporter
import my.noveldokusha.tooling.quick_setup.transfer.TransferProgress
import my.noveldokusha.tooling.quick_setup.transfer.resilience.TransferSessionStore
import my.noveldokusha.tooling.quick_setup.transfer.resilience.TransferSummary
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class QuickSetupViewModel @Inject constructor(
    application: Application,
    private val exporter: QuickSetupExporter,
    private val importer: QuickSetupImporter,
) : AndroidViewModel(application) {

    private val nsdAdvertiser = NsdAdvertiser(application)
    private val sessionStore = TransferSessionStore(application)

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState: StateFlow<ReceiveState> = _receiveState.asStateFlow()

    sealed class SendState {
        data object Idle : SendState()
        data class ServerRunning(
            val port: Int,
            val sessionToken: String,
            val hostIp: String? = null,
            val hostName: String? = null,
        ) : SendState()
        data class WaitingForAccept(val deviceName: String) : SendState()
        data class InProgress(val progress: TransferProgress) : SendState()
        data class Completed(val summary: TransferSummary? = null) : SendState()
        data class Error(val message: String) : SendState()
    }

    sealed class ReceiveState {
        data object Idle : ReceiveState()
        data class Connecting(val host: String, val port: Int) : ReceiveState()
        data class InProgress(val progress: TransferProgress) : ReceiveState()
        data class Completed(val summary: TransferSummary) : ReceiveState()
        data class Error(val message: String, val summary: TransferSummary? = null) : ReceiveState()
        data object Resuming : ReceiveState()
    }

    private var client: QuickSetupClient? = null
    private var sessionToken: String = ""

    val hasResumableSession: Boolean
        get() = sessionStore.hasActiveSession()

    /** Host/port of an interrupted session, for prefilling the resume UI after a restart. */
    val resumableSessionEndpoint: Pair<String, Int>?
        get() = sessionStore.loadSession()
            ?.let { s -> s.host?.let { it to s.port } }

    fun startSendServer(port: Int = QuickSetupServerService.DEFAULT_PORT) {
        sessionToken = PairingManager.generateSessionToken()
        QuickSetupAcceptGate.reset()
        val hostIp = getLocalIpAddress()
        val deviceName = Build.MODEL ?: "Android Device"

        _sendState.value = SendState.ServerRunning(
            port = port,
            sessionToken = sessionToken,
            hostIp = hostIp,
            hostName = deviceName,
        )

        // Surface incoming connection requests as an accept dialog
        viewModelScope.launch {
            QuickSetupAcceptGate.pendingClient.collect { pending ->
                if (pending != null && _sendState.value is SendState.ServerRunning) {
                    _sendState.value = SendState.WaitingForAccept(pending.deviceName)
                }
            }
        }

        val context = getApplication<Application>()
        QuickSetupServerService.start(context, port, sessionToken)

        nsdAdvertiser.register(
            port = port,
            deviceName = deviceName,
            onStateChanged = { state ->
                when (state) {
                    is NsdAdvertiser.RegistrationState.Registered -> {
                        Timber.i("NSD registered successfully")
                    }
                    is NsdAdvertiser.RegistrationState.Error -> {
                        Timber.e("NSD registration failed: ${state.message}")
                    }
                    else -> {}
                }
            }
        )
    }

    fun stopSendServer() {
        nsdAdvertiser.unregister()
        QuickSetupAcceptGate.reset()
        val context = getApplication<Application>()
        QuickSetupServerService.stop(context)
        _sendState.value = SendState.Idle
    }

    fun acceptConnection() {
        val currentState = _sendState.value
        if (currentState is SendState.WaitingForAccept) {
            QuickSetupAcceptGate.accept()
            _sendState.value = SendState.InProgress(
                TransferProgress(
                    phase = TransferProgress.Phase.CONNECTING,
                    itemsTransferred = 0, totalItems = 0,
                    bytesTransferred = 0, totalBytes = 0,
                )
            )
        }
    }

    fun rejectConnection() {
        QuickSetupAcceptGate.reject()
        _sendState.value = SendState.ServerRunning(
            port = QuickSetupServerService.DEFAULT_PORT,
            sessionToken = sessionToken,
            hostIp = getLocalIpAddress(),
            hostName = Build.MODEL,
        )
    }

    fun startReceive(host: String, port: Int, sessionToken: String) {
        _receiveState.value = ReceiveState.Connecting(host, port)
        viewModelScope.launch {
            try {
                val c = QuickSetupClient(
                    host = host,
                    port = port,
                    sessionToken = sessionToken,
                    importer = importer,
                    sessionStore = sessionStore,
                    onProgress = { progress ->
                        _receiveState.value = ReceiveState.InProgress(progress)
                    }
                )
                client = c
                val summary = c.transferAll()
                _receiveState.value = ReceiveState.Completed(summary)
            } catch (e: Exception) {
                Timber.e(e, "Receive failed")
                _receiveState.value = ReceiveState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resumeReceive(host: String, port: Int, sessionToken: String) {
        _receiveState.value = ReceiveState.Resuming
        startReceive(host, port, sessionToken)
    }

    fun cancelTransfer() {
        client?.cancel()
        val currentState = _receiveState.value
        if (currentState is ReceiveState.InProgress) {
            _receiveState.value = ReceiveState.Error("Transfer cancelled")
        }
    }

    fun resetSendState() {
        nsdAdvertiser.unregister()
        QuickSetupAcceptGate.reset()
        val context = getApplication<Application>()
        QuickSetupServerService.stop(context)
        _sendState.value = SendState.Idle
    }

    fun resetReceiveState() {
        _receiveState.value = ReceiveState.Idle
        importer.reset()
    }

    fun clearSession() {
        sessionStore.clearSession()
    }

    private fun getLocalIpAddress(): String? {
        val context = getApplication<Application>()
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Application.WIFI_SERVICE) as? android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val ip = wifiManager?.connectionInfo?.ipAddress
        if (ip != null && ip != 0) {
            return String.format(
                "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff,
            )
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        client?.close()
        client = null
        nsdAdvertiser.unregister()
        stopSendServer()
    }
}
