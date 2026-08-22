package my.noveldokusha.tooling.quick_setup.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import timber.log.Timber

/**
 * Discovers Quick Setup services on the local network via NSD.
 * The target device uses this to find source devices advertising Quick Setup.
 *
 * Security: The session token is NOT included in NSD records.
 * After discovering a device, the user must enter the token manually
 * (shown on the source device's QR code screen).
 */
class NsdDiscoverer(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = NsdAdvertiser.SERVICE_TYPE
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var isDiscovering = false

    data class DiscoveredDevice(
        val name: String,
        val host: String,
        val port: Int,
        val deviceName: String,
    )

    sealed class DiscoveryState {
        data object Idle : DiscoveryState()
        data object Discovering : DiscoveryState()
        data class DeviceFound(val device: DiscoveredDevice) : DiscoveryState()
        data class DeviceLost(val deviceName: String) : DiscoveryState()
        data class Error(val message: String) : DiscoveryState()
    }

    fun startDiscovery(onStateChanged: (DiscoveryState) -> Unit) {
        if (isDiscovering) {
            Timber.w("NSD discovery already active")
            return
        }

        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD resolve failed for ${serviceInfo.serviceName}: code $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val deviceName = if (serviceInfo.attributes.isNotEmpty()) {
                    serviceInfo.attributes["device"]?.let { String(it) } ?: "Unknown"
                } else "Unknown"

                val device = DiscoveredDevice(
                    name = serviceInfo.serviceName,
                    host = host,
                    port = port,
                    deviceName = deviceName,
                )

                Timber.i("NSD service resolved: ${device.name} at ${device.host}:${device.port}")
                onStateChanged(DiscoveryState.DeviceFound(device))
            }
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                Timber.i("NSD discovery started")
                isDiscovering = true
                onStateChanged(DiscoveryState.Discovering)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Timber.i("NSD service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    try {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to resolve service: ${serviceInfo.serviceName}")
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Timber.i("NSD service lost: ${serviceInfo.serviceName}")
                onStateChanged(DiscoveryState.DeviceLost(serviceInfo.serviceName))
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Timber.i("NSD discovery stopped")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("NSD discovery failed to start: code $errorCode")
                isDiscovering = false
                onStateChanged(DiscoveryState.Error("Discovery failed (code: $errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("NSD discovery failed to stop: code $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start NSD discovery")
            onStateChanged(DiscoveryState.Error("Failed to start discovery: ${e.message}"))
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop NSD discovery")
            }
        }
        discoveryListener = null
        resolveListener = null
        isDiscovering = false
    }
}
