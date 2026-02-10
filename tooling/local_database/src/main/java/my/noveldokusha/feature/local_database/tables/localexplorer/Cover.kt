package my.noveldokusha.feature.local_database.tables.localexplorer

import android.graphics.Bitmap

data class Cover(
    val bitmap: Bitmap? = null,
    val text: String? = null,
    val vector: Int? = null
)