package my.noveldokusha

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.EntryPoints
import dagger.hilt.android.HiltAndroidApp
import my.noveldokusha.di.HiltAppEntryPoint
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.tooling.application_workers.setup.PeriodicWorkersInitializer
import my.noveldokusha.tooling.local_server_sync.sync.QuickProgressSyncWorker
import timber.log.Timber
import javax.inject.Inject


@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var networkClient: NetworkClient

    @Inject
    lateinit var periodicWorkersInitializer: PeriodicWorkersInitializer

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        periodicWorkersInitializer.init()

        // Trigger a quick library + chapter sync every time the app comes to
        // the foreground. The worker no-ops if the user isn't signed in or
        // no server URL is configured.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                QuickProgressSyncWorker.enqueue(this@App)
            }
        })
    }

    override fun newImageLoader(): ImageLoader = when (val networkClient = networkClient) {
        is ScraperNetworkClient -> ImageLoader
            .Builder(this)
            .okHttpClient(networkClient.client)
            .build()

        else -> ImageLoader(this)
    }

    // WorkManager
    override val workManagerConfiguration: Configuration by lazy {
        val appWorkerFactory = EntryPoints
            .get(this, HiltAppEntryPoint::class.java)
            .workerFactory()

        Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .setWorkerFactory(appWorkerFactory)
            .build()
    }

    companion object {
        @get:Synchronized
        lateinit var instance: App
            private set
    }
}
