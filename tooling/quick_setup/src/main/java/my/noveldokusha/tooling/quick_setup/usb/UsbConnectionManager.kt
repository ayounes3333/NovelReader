package my.noveldokusha.tooling.quick_setup.usb

import android.content.Context
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * High-level manager for USB Quick Setup connections.
 * Handles role detection (host vs accessory) and provides a unified API
 * for establishing USB transfer channels.
 */
class UsbConnectionManager(private val context: Context) {

    private val hostConnection = AoaHostConnection(context)
    private val accessoryConnection = AoaAccessoryConnection(context)
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var activeChannel: UsbTransferChannel? = null

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object DetectingRole : ConnectionState()
        data object RequestingPermission : ConnectionState()
        data object WaitingForReenumeration : ConnectionState()
        data class Connected(val channel: UsbTransferChannel, val role: UsbRole) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    enum class UsbRole {
        HOST,
        ACCESSORY,
    }

    fun detectAndConnect(onStateChanged: (ConnectionState) -> Unit) {
        onStateChanged(ConnectionState.DetectingRole)

        val attachedAccessory = accessoryConnection.findAttachedAccessory()
        if (attachedAccessory != null) {
            Timber.i("USB accessory detected - this device is the accessory")
            connectAsAccessory(onStateChanged)
            return
        }

        val supportedDevice = hostConnection.findSupportedDevice()
        if (supportedDevice != null) {
            Timber.i("USB device detected - this device is the host")
            connectAsHost(supportedDevice, onStateChanged)
            return
        }

        onStateChanged(ConnectionState.Error(
            "No USB device detected. Connect a USB cable between devices."
        ))
    }

    private fun connectAsHost(
        device: android.hardware.usb.UsbDevice,
        onStateChanged: (ConnectionState) -> Unit
    ) {
        onStateChanged(ConnectionState.RequestingPermission)

        hostConnection.requestConnection(device) { result ->
            when (result) {
                is AoaHostConnection.ConnectionResult.Success -> {
                    Timber.i("Connected as USB host")
                    activeChannel = result.channel
                    onStateChanged(ConnectionState.Connected(result.channel, UsbRole.HOST))
                }
                is AoaHostConnection.ConnectionResult.Error -> {
                    onStateChanged(ConnectionState.Error(result.message))
                }
                is AoaHostConnection.ConnectionResult.PermissionRequired -> {
                    Timber.i("Device switching to accessory mode, waiting for re-enumeration...")
                    onStateChanged(ConnectionState.WaitingForReenumeration)
                    hostConnection.waitForReenumeration { reenumResult ->
                        when (reenumResult) {
                            is AoaHostConnection.ConnectionResult.Success -> {
                                activeChannel = reenumResult.channel
                                onStateChanged(ConnectionState.Connected(reenumResult.channel, UsbRole.HOST))
                            }
                            is AoaHostConnection.ConnectionResult.Error -> {
                                onStateChanged(ConnectionState.Error(reenumResult.message))
                            }
                            is AoaHostConnection.ConnectionResult.PermissionRequired -> {
                                // Should not happen during re-enumeration
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectAsAccessory(onStateChanged: (ConnectionState) -> Unit) {
        accessoryConnection.startAutoConnect(
            onChannelReady = { channel ->
                Timber.i("Connected as USB accessory")
                activeChannel = channel
                onStateChanged(ConnectionState.Connected(channel, UsbRole.ACCESSORY))
            },
            onError = { error ->
                onStateChanged(ConnectionState.Error(error))
            }
        )
    }

    fun disconnect() {
        // Closing the channel also closes the underlying UsbDeviceConnection /
        // PFD, which unblocks any reader stuck in bulkTransfer and releases
        // the claimed interface so the devices can reconnect.
        activeChannel?.close()
        activeChannel = null
        hostConnection.unregister()
        accessoryConnection.unregister()
    }
}
