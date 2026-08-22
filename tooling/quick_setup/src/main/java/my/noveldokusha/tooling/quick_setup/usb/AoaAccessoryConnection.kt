package my.noveldokusha.tooling.quick_setup.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Manages the USB accessory role for AOA connections.
 * Handles accessory mode activation when this device is connected to a host,
 * and opens the bidirectional byte stream for data transfer.
 */
class AoaAccessoryConnection(private val context: Context) {

    companion object {
        private const val ACTION_USB_ACCESSORY_ATTACHED =
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED"
        private const val ACTION_USB_ACCESSORY_DETACHED =
            "android.hardware.usb.action.USB_ACCESSORY_DETACHED"
        private const val ACTION_USB_PERMISSION =
            "my.noveldokusha.tooling.quick_setup.USB_ACCESSORY_PERMISSION"
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val executor = Executors.newSingleThreadExecutor()
    private var accessoryReceiver: BroadcastReceiver? = null
    private var permissionReceiver: BroadcastReceiver? = null

    sealed class ConnectionResult {
        data class Success(val channel: UsbTransferChannel) : ConnectionResult()
        data class Error(val message: String) : ConnectionResult()
    }

    fun findAttachedAccessory(): UsbAccessory? {
        return usbManager.accessoryList?.firstOrNull()
    }

    fun listenForAccessory(
        onAttached: (UsbAccessory) -> Unit,
        onDetached: () -> Unit
    ) {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_ACCESSORY_ATTACHED)
            addAction(ACTION_USB_ACCESSORY_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        accessoryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_USB_ACCESSORY_ATTACHED -> {
                        val accessory = usbManager.accessoryList?.firstOrNull()
                        if (accessory != null) {
                            Timber.i("USB accessory attached: ${accessory.description}")
                            onAttached(accessory)
                        }
                    }
                    ACTION_USB_ACCESSORY_DETACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Timber.i("USB accessory detached")
                        close()
                        onDetached()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(accessoryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(accessoryReceiver, filter)
        }
    }

    fun connectToAccessory(accessory: UsbAccessory): ConnectionResult {
        return try {
            val pfd = usbManager.openAccessory(accessory)
                ?: return ConnectionResult.Error("Failed to open accessory")
            // AutoClose streams keep the ParcelFileDescriptor alive and close it
            // with the stream — a bare FileInputStream(pfd.fileDescriptor) would
            // let the PFD get finalized by GC mid-transfer, invalidating the fd.
            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(pfd)

            val channel = UsbTransferChannel(
                name = "USB-Accessory:${accessory.description}",
                inputStream = inputStream,
                outputStream = outputStream,
                connection = null,
            )

            ConnectionResult.Success(channel)
        } catch (e: Exception) {
            ConnectionResult.Error("Failed to connect to accessory: ${e.message}")
        }
    }

    /**
     * Connect to the accessory, requesting user permission first if needed.
     * Permission is granted implicitly when the app is launched via the
     * USB_ACCESSORY_ATTACHED manifest intent-filter, but is required when the
     * user navigates to the USB screen manually.
     */
    fun connectWithPermission(
        accessory: UsbAccessory,
        onResult: (ConnectionResult) -> Unit,
    ) {
        if (usbManager.hasPermission(accessory)) {
            executor.execute { onResult(connectToAccessory(accessory)) }
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Explicit intent required: API 34+ forbids mutable PendingIntents
        // with implicit intents (mutability is required for USB permission).
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
        )

        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    executor.execute { onResult(connectToAccessory(accessory)) }
                } else {
                    onResult(ConnectionResult.Error("USB accessory permission denied"))
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
        usbManager.requestPermission(accessory, permissionIntent)
    }

    fun startAutoConnect(onChannelReady: (UsbTransferChannel) -> Unit, onError: (String) -> Unit) {
        val existing = findAttachedAccessory()
        if (existing != null) {
            connectWithPermission(existing) { result ->
                when (result) {
                    is ConnectionResult.Success -> onChannelReady(result.channel)
                    is ConnectionResult.Error -> onError(result.message)
                }
            }
        }

        listenForAccessory(
            onAttached = { accessory ->
                connectWithPermission(accessory) { result ->
                    when (result) {
                        is ConnectionResult.Success -> onChannelReady(result.channel)
                        is ConnectionResult.Error -> onError(result.message)
                    }
                }
            },
            onDetached = {
                Timber.i("Accessory detached")
            }
        )
    }

    fun close() {
        // Accessory file descriptors are managed by the channel lifecycle
    }

    fun unregister() {
        accessoryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        accessoryReceiver = null
        permissionReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        permissionReceiver = null
        close()
    }
}
