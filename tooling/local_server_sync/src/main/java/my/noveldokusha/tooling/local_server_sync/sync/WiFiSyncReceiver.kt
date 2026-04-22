package my.noveldokusha.tooling.local_server_sync.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import timber.log.Timber

/**
 * Listens for network connectivity changes and triggers a background sync
 * when an unmetered (WiFi) connection becomes available.
 */
class WiFiSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Timber.d("WiFiSyncReceiver: WiFi connected, scheduling sync")
            WorkManager.getInstance(context).enqueueUniqueWork(
                WiFiSyncWorker.TAG,
                ExistingWorkPolicy.REPLACE,
                WiFiSyncWorker.createOneTimeRequest()
            )
        }
    }
}
