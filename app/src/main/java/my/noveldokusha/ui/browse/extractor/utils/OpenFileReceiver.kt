package my.noveldokusha.ui.browse.extractor.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import my.noveldokusha.data.BookMetadata
import my.noveldokusha.features.chaptersList.ChaptersActivity

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