package my.noveldokusha.tooling.quick_setup.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared gate between the Quick Setup server (running in the foreground service)
 * and the send-flow UI. The server refuses to serve any data until the user on
 * the source device explicitly accepts the incoming connection.
 *
 * A rejection is sticky for the current server session: the rejected client keeps
 * receiving 403 until the user restarts the send server (which issues a new token).
 */
object QuickSetupAcceptGate {

    data class PendingClient(val deviceName: String)

    private val _pendingClient = MutableStateFlow<PendingClient?>(null)
    val pendingClient: StateFlow<PendingClient?> = _pendingClient.asStateFlow()

    @Volatile
    var isAccepted: Boolean = false
        private set

    @Volatile
    var isRejected: Boolean = false
        private set

    /** Called by the server when an authenticated client asks for the manifest. */
    fun requestAccept(deviceName: String) {
        if (isAccepted || isRejected) return
        if (_pendingClient.value == null) {
            _pendingClient.value = PendingClient(deviceName)
        }
    }

    fun accept() {
        isAccepted = true
        isRejected = false
        _pendingClient.value = null
    }

    fun reject() {
        isRejected = true
        isAccepted = false
        _pendingClient.value = null
    }

    fun reset() {
        isAccepted = false
        isRejected = false
        _pendingClient.value = null
    }
}
