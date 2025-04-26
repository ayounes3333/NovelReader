package my.noveldokusha.features.localexplorer.extractor.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.features.chapterslist.ChaptersActivity

class OpenFileReceiver: BroadcastReceiver() {

    companion object {
        const val ACTION = "my.noveldokusha.openFileReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            intent?.let {
                val title = intent.getStringExtra("title") ?: ""
                val url = intent.getStringExtra("url") ?: ""
                val openIntent = ChaptersActivity.IntentData(
                    context,
                    bookMetadata = BookMetadata(title = title, url = url)
                )
                context.startActivity(openIntent)
            }
        }
    }
}