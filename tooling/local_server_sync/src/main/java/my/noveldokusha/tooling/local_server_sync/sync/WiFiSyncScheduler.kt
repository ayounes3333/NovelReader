package my.noveldokusha.tooling.local_server_sync.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
}
