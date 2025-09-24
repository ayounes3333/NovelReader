package my.noveldokusha.features.main

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aliyounes.aurui.ui.theme.AurUITheme
import com.aliyounes.aurui.components.*
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldoksuha.coreui.BaseActivity
import my.noveldoksuha.coreui.components.AnimatedTransition
import my.noveldoksuha.coreui.theme.Theme
import my.noveldokusha.R
import my.noveldokusha.catalogexplorer.CatalogExplorerScreen
import my.noveldokusha.features.localexplorer.FileManager
import my.noveldokusha.features.localexplorer.extractor.utils.OpenFileReceiver
import my.noveldokusha.libraryexplorer.LibraryScreen
import my.noveldokusha.settings.SettingsScreen
import my.noveldokusha.tooling.epub_importer.EpubImportService

// Convert pages to AurTabItem format
private fun getTabItems(): List<AurTabItem> = listOf(
    AurTabItem("Library", Icons.Default.Home),
    AurTabItem("Finder", Icons.Default.MenuBook),
    AurTabItem("Settings", Icons.Default.Settings)
)


@OptIn(ExperimentalAnimationApi::class)
@AndroidEntryPoint
open class MainActivity : BaseActivity() {
    private lateinit var openFileReceiver: OpenFileReceiver
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPushNotificationPermission()

        FileManager.init(this)
        configureReceiver()

        setContent {
            var activePageIndex by rememberSaveable { mutableIntStateOf(0) }
            val tabs = getTabItems()

            BackHandler(enabled = activePageIndex != 0) {
                activePageIndex = 0
            }

            AurUITheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AnimatedTransition(targetState = activePageIndex) {
                            when (it) {
                                0 -> LibraryScreen()
                                1 -> CatalogExplorerScreen()
                                2 -> SettingsScreen()
                            }
                        }
                    }
                    AurTabBar(
                        selectedIndex = activePageIndex,
                        onTabSelected = { activePageIndex = it },
                        tabs = tabs,
                        style = AurTabBarStyle.Pill
                    )
                }
            }
        }

        handleIntent(intent)
    }

    private fun configureReceiver() {
        val filter = IntentFilter()
        filter.addAction(OpenFileReceiver.ACTION)
        openFileReceiver = OpenFileReceiver()
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(openFileReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(openFileReceiver, filter, RECEIVER_NOT_EXPORTED)
        }
    }

    private fun requestPushNotificationPermission() {
        // check if sdk level is more than 33
        if (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
            return
        }

        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (result != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return
        val type = intent.type

        when (action) {
            Intent.ACTION_SEND -> {
                if (type == "application/epub+zip") {
                    handleSharedEpub(intent)
                }
            }

            Intent.ACTION_VIEW -> {
                handleViewedEpub(intent)
            }
        }
    }

    private fun handleViewedEpub(intent: Intent) {
        val epubUri: Uri? = intent.data
        if (epubUri != null) {
            EpubImportService.start(ctx = this, uri = epubUri)
        }
    }

    private fun handleSharedEpub(intent: Intent) {
        val epubUri: Uri? = IntentCompat.getParcelableExtra(
            intent, Intent.EXTRA_STREAM, Uri::class.java
        )
        if (epubUri != null) {
            EpubImportService.start(ctx = this, uri = epubUri)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(openFileReceiver)
        super.onDestroy()
    }
}

