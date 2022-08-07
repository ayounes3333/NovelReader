package my.noveldokusha

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App @Inject constructor() : Application() {
    override fun onCreate() {
        _instance = this
        super.onCreate()
    }

    companion object {
        private var _instance: App? = null
        val instance get() = _instance!!
    }
}
