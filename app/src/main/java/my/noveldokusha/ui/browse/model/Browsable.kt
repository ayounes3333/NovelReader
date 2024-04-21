package my.noveldokusha.ui.browse.model

import my.noveldokusha.ui.browse.extractor.data.NovelFileInfo
import java.io.File

data class Browsable(
    val file: File,
    val novelFileInfo: NovelFileInfo? = null
) {
    val isDirectory: Boolean
        get() = novelFileInfo == null && file.isDirectory
}
