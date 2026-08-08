package my.noveldokusha.tooling.local_server_sync.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers a system NetworkCallback to detect WiFi connectivity changes.
 * When WiFi connects and auto-sync is enabled, schedules a [WiFiSyncWorker].
 */
@Singleton
class WiFiSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStorage: AuthTokenStorage
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun register() {
        if (networkCallback != null) return // already registered

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("WiFiSyncScheduler: WiFi available")
                if (!tokenStorage.isAutoSyncEnabled()) return
                if (tokenStorage.getServerUrls().isEmpty()) return
                if (!isOnHomeWifi()) {
                    Timber.d("WiFiSyncScheduler: not on a known home WiFi, skipping bulk sync")
                    return
                }

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WiFiSyncWorker.TAG,
                    ExistingWorkPolicy.REPLACE,
                    WiFiSyncWorker.createOneTimeRequest()
                )
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
        Timber.d("WiFiSyncScheduler: registered network callback")
    }

    fun unregister() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
            Timber.d("WiFiSyncScheduler: unregistered network callback")
        }
    }

    /**
     * Returns true when the current WiFi connection's BSSID is one the user
     * marked as "home WiFi" in settings, OR no home BSSIDs have been
     * configured yet (in which case any WiFi counts).
     *
     * Requires `ACCESS_FINE_LOCATION` on API 27+; if the permission is
     * missing, `connectionInfo.bssid` returns `"02:00:00:00:00:00"`, which we
     * treat as "unknown" and refuse to bulk-sync unless the home list is
     * empty.
     */
    @Suppress("DEPRECATION")
    private fun isOnHomeWifi(): Boolean {
        val home = tokenStorage.getHomeBssids()
        if (home.isEmpty()) return true
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return false
            val bssid = wifi.connectionInfo?.bssid?.lowercase()
            if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") false
            else home.any { it.equals(bssid, ignoreCase = true) }
        } catch (e: SecurityException) {
            Timber.w(e, "WiFiSyncScheduler: BSSID read denied")
            false
        }
    }
}
