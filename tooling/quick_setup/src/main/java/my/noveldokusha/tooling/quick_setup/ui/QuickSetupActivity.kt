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
import my.noveldokusha.tooling.quick_setup.ui.ReceiveFlowScreen
import my.noveldokusha.tooling.quick_setup.ui.SendFlowScreen

@AndroidEntryPoint
class QuickSetupActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val MODE_SEND = "send"
        private const val MODE_RECEIVE = "receive"

        fun sendIntent(context: Context): Intent {
            return Intent(context, QuickSetupActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_SEND)
            }
        }

        fun receiveIntent(context: Context): Intent {
            return Intent(context, QuickSetupActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_RECEIVE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SEND

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (mode) {
                    MODE_SEND -> SendFlowScreen(onDone = { finish() })
                    MODE_RECEIVE -> ReceiveFlowScreen(onDone = { finish() })
                }
            }
        }
    }
}
