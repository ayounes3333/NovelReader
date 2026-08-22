package my.noveldokusha.tooling.quick_setup.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import timber.log.Timber

/**
 * Advertises the Quick Setup server via NSD (Network Service Discovery).
 * The source device registers itself so target devices on the same network can find it.
 *
 * Security: The session token is NOT included in NSD TXT records.
 * The target device must obtain the token via QR code scan or manual entry.
 */
class NsdAdvertiser(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_novelreader-qs._tcp."
        const val SERVICE_NAME = "NovelDokusha QuickSetup"
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isRegistered = false

    sealed class RegistrationState {
        data object Idle : RegistrationState()
        data object Registering : RegistrationState()
        data class Registered(val serviceInfo: NsdServiceInfo) : RegistrationState()
        data class Error(val message: String) : RegistrationState()
    }

    fun register(
        port: Int,
        deviceName: String,
        onStateChanged: (RegistrationState) -> Unit
    ) {
        if (isRegistered) {
            Timber.w("NSD service already registered")
            return
        }

        onStateChanged(RegistrationState.Registering)

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("device", deviceName)
                setAttribute("version", "1")
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                val registeredName = info.serviceName
                Timber.i("NSD service registered as: $registeredName")
                isRegistered = true
                onStateChanged(RegistrationState.Registered(info))
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD registration failed with error code: $errorCode")
                onStateChanged(RegistrationState.Error("Registration failed (code: $errorCode)"))
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Timber.i("NSD service unregistered")
                isRegistered = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD unregistration failed with error code: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register NSD service")
            onStateChanged(RegistrationState.Error("Failed to register: ${e.message}"))
        }
    }

    fun unregister() {
        if (!isRegistered) return
        registrationListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Timber.e(e, "Failed to unregister NSD service")
            }
        }
        registrationListener = null
        isRegistered = false
    }
}
