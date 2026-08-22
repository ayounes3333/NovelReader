package my.noveldokusha.tooling.quick_setup.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Build
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Manages the USB host role for AOA connections.
 * Handles device enumeration, AOA protocol negotiation, and stream setup.
 *
 * After switching a device to accessory mode, the host waits for the device to
 * re-enumerate as an AOA accessory device (VID 0x18D1), then claims its bulk
 * interface and communicates through bulk transfers.
 */
class AoaHostConnection(private val context: Context) {

    companion object {
        const val AOA_VENDOR_ID = 0x18D1
        const val AOA_ACCESSORY_PRODUCT_ID = 0x2D00
        const val AOA_ACCESSORY_ADB_PRODUCT_ID = 0x2D01

        private const val AOA_GET_PROTOCOL = 51
        private const val AOA_SEND_IDENT = 52
        private const val AOA_START_ACCESSORY = 53

        private const val CONTROL_REQUEST_TYPE_OUT = 0x40
        private const val CONTROL_REQUEST_TYPE_IN = 0xC0
        private const val USB_TIMEOUT_MS = 5000
        private const val REENUMERATION_TIMEOUT_MS = 15_000L
        private const val REENUMERATION_POLL_MS = 500L

        private const val ACTION_USB_PERMISSION =
            "my.noveldokusha.tooling.quick_setup.USB_PERMISSION"
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val executor = Executors.newSingleThreadExecutor()
    private var permissionReceiver: BroadcastReceiver? = null

    sealed class ConnectionResult {
        data class Success(val channel: UsbTransferChannel) : ConnectionResult()
        data class Error(val message: String) : ConnectionResult()
        data object PermissionRequired : ConnectionResult()
    }

    fun findSupportedDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            isAccessoryDevice(device) || isAoaCapableDevice(device)
        }
    }

    fun isAccessoryDevice(device: UsbDevice): Boolean {
        return device.vendorId == AOA_VENDOR_ID &&
                (device.productId == AOA_ACCESSORY_PRODUCT_ID ||
                        device.productId == AOA_ACCESSORY_ADB_PRODUCT_ID)
    }

    fun isAoaCapableDevice(device: UsbDevice): Boolean {
        return !isAccessoryDevice(device)
    }

    fun requestConnection(
        device: UsbDevice,
        onResult: (ConnectionResult) -> Unit
    ) {
        if (usbManager.hasPermission(device)) {
            executor.execute {
                val result = connectToDevice(device)
                onResult(result)
            }
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            // The intent must be explicit (setPackage): API 34+ forbids mutable
            // PendingIntents with implicit intents, and the USB permission
            // PendingIntent must be mutable so the system can attach extras.
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )

            permissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false
                        )
                        @Suppress("DEPRECATION")
                        val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (granted && dev != null) {
                            executor.execute {
                                val result = connectToDevice(dev)
                                onResult(result)
                            }
                        } else {
                            onResult(ConnectionResult.Error("USB permission denied"))
                        }
                    }
                }
            }

            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }

            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun connectToDevice(device: UsbDevice): ConnectionResult {
        return if (isAccessoryDevice(device)) {
            openAccessoryModeDevice(device)
        } else {
            val connection = usbManager.openDevice(device)
                ?: return ConnectionResult.Error("Failed to open USB device")
            try {
                switchToAccessory(connection, device)
            } catch (e: Exception) {
                connection.close()
                ConnectionResult.Error("USB connection failed: ${e.message}")
            }
        }
    }

    private fun switchToAccessory(
        connection: UsbDeviceConnection,
        device: UsbDevice
    ): ConnectionResult {
        Timber.i("Attempting AOA negotiation with device: ${device.deviceName}")

        val protocolVersion = getAoaProtocolVersion(connection)
        if (protocolVersion == 0) {
            connection.close()
            return ConnectionResult.Error("Device does not support AOA (protocol version 0)")
        }
        Timber.i("AOA protocol version: $protocolVersion")

        sendAccessoryString(connection, 0, "NovelDokusha")
        sendAccessoryString(connection, 1, "NovelReader")
        sendAccessoryString(connection, 2, "Quick Setup Transfer")
        sendAccessoryString(connection, 3, "1.0")
        sendAccessoryString(connection, 4, "https://noveldokusha.app")
        sendAccessoryString(connection, 5, "noveldokusha-qs-001")

        val result = connection.controlTransfer(
            CONTROL_REQUEST_TYPE_OUT, AOA_START_ACCESSORY,
            0, 0, null, 0, USB_TIMEOUT_MS
        )
        connection.close()

        if (result < 0) {
            return ConnectionResult.Error("Failed to start accessory mode")
        }

        Timber.i("Accessory mode started. Device will re-enumerate.")
        return ConnectionResult.PermissionRequired
    }

    private fun getAoaProtocolVersion(connection: UsbDeviceConnection): Int {
        val buffer = ByteArray(2)
        val result = connection.controlTransfer(
            CONTROL_REQUEST_TYPE_IN, AOA_GET_PROTOCOL,
            0, 0, buffer, 2, USB_TIMEOUT_MS
        )
        return if (result == 2) {
            (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
        } else {
            0
        }
    }

    private fun sendAccessoryString(connection: UsbDeviceConnection, index: Int, value: String) {
        // AOA spec: SEND_STRING uses value=0, index=stringId, and the payload
        // must be a zero-terminated UTF-8 string.
        val bytes = (value + "\u0000").toByteArray(Charsets.UTF_8)
        val result = connection.controlTransfer(
            CONTROL_REQUEST_TYPE_OUT, AOA_SEND_IDENT,
            0, index, bytes, bytes.size, USB_TIMEOUT_MS
        )
        if (result < 0) {
            Timber.w("AOA SEND_STRING failed for index=$index value=$value")
        }
    }

    /**
     * Wait for the peer to re-enumerate as an AOA accessory device (VID 0x18D1,
     * PID 0x2D00/0x2D01) by polling the host's device list.
     *
     * Note: ACTION_USB_DEVICE_ATTACHED is only delivered to activities via
     * manifest intent-filters, never to runtime-registered receivers, so
     * polling is the reliable approach here. Re-enumeration typically takes ~1s.
     */
    fun waitForReenumeration(onResult: (ConnectionResult) -> Unit) {
        executor.execute {
            val deadline = System.currentTimeMillis() + REENUMERATION_TIMEOUT_MS
            var device: UsbDevice? = null
            while (System.currentTimeMillis() < deadline) {
                device = usbManager.deviceList.values.firstOrNull { isAccessoryDevice(it) }
                if (device != null) break
                try {
                    Thread.sleep(REENUMERATION_POLL_MS)
                } catch (_: InterruptedException) {
                    return@execute
                }
            }
            if (device == null) {
                onResult(ConnectionResult.Error("Device did not re-enumerate as accessory"))
                return@execute
            }
            Timber.i("Device re-enumerated as accessory: ${device.deviceName}")
            // The re-enumerated device is a new USB device — permission is
            // required again. requestConnection handles the permission flow
            // and then routes into openAccessoryModeDevice().
            requestConnection(device, onResult)
        }
    }

    /**
     * Open the re-enumerated AOA device from the host side.
     * The host must claim the AOA bulk interface and communicate through
     * bulk transfers — UsbManager.openAccessory() only works on the accessory side.
     */
    private fun openAccessoryModeDevice(device: UsbDevice): ConnectionResult {
        val connection = usbManager.openDevice(device)
            ?: return ConnectionResult.Error("Failed to open re-enumerated accessory device")

        val usbInterface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { intf ->
                var hasIn = false
                var hasOut = false
                for (i in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(i)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                        else hasOut = true
                    }
                }
                hasIn && hasOut
            }
            ?: run {
                connection.close()
                return ConnectionResult.Error("No bulk interface on accessory device")
            }

        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            return ConnectionResult.Error("Failed to claim accessory interface")
        }

        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) endpointIn = ep else endpointOut = ep
        }
        if (endpointIn == null || endpointOut == null) {
            connection.releaseInterface(usbInterface)
            connection.close()
            return ConnectionResult.Error("Missing bulk endpoints on accessory device")
        }

        val channel = UsbTransferChannel(
            name = "USB-Host:${device.deviceName}",
            inputStream = UsbBulkInputStream(connection, endpointIn),
            outputStream = UsbBulkOutputStream(connection, endpointOut),
            connection = connection,
        )
        Timber.i("Host bulk channel established with ${device.deviceName}")
        return ConnectionResult.Success(channel)
    }

    fun unregister() {
        permissionReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        permissionReceiver = null
    }
}
