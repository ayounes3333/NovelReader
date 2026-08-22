package my.noveldokusha.tooling.quick_setup.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity launched automatically when a USB accessory is attached.
 * Routes to the UsbConnectionScreen for USB-based transfer.
 */
@AndroidEntryPoint
class UsbQuickSetupActivity : ComponentActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, UsbQuickSetupActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                UsbConnectionScreen(onDone = { finish() })
            }
        }
    }
}
