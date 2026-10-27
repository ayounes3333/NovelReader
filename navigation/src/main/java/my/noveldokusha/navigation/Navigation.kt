package my.noveldokusha.navigation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import my.noveldokusha.feature.local_database.BookMetadata
import javax.inject.Inject

interface NavigationRoutes {

    companion object {
        /**
         * Boolean intent extra: when set on a [chapters] intent, the chapters
         * screen opens with the AI bulk task sheet expanded (notification
         * tap-through).
         */
        const val EXTRA_OPEN_AI_BULK_SHEET = "my.noveldokusha.extra.OPEN_AI_BULK_SHEET"
    }

    fun main(context: Context): Intent

    fun chapters(context: Context, bookMetadata: BookMetadata): Intent

    fun webView(context: Context, url: String): Intent

    fun reader(
        context: Context,
        bookUrl: String,
        chapterUrl: String,
        scrollToSpeakingItem: Boolean = false
    ): Intent

    fun databaseSearch(
        context: Context,
        input: String,
        databaseUrlBase: String = "https://www.novelupdates.com/"
    ): Intent

    fun databaseSearch(
        context: Context,
        databaseBaseUrl: String
    ): Intent

    fun globalSearch(context: Context, text: String): Intent
    fun sourceCatalog(context: Context, sourceBaseUrl: String): Intent
}

@HiltViewModel
class NavigationRouteViewModel @Inject constructor(
    private val appNavigationRoutes: NavigationRoutes
) : NavigationRoutes by appNavigationRoutes, ViewModel()