package my.noveldokusha.features.localexplorer.model

import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo
import java.io.File

data class Browsable(
    val file: File,
    val novelFileInfo: NovelFileInfo? = null
) {
    val isDirectory: Boolean
        get() = novelFileInfo == null && file.isDirectory
}
