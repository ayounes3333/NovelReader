package my.noveldokusha.ui.browse.extractor.utils

import my.noveldokusha.ui.browse.extractor.EPubExtractor
import my.noveldokusha.ui.browse.extractor.FileExtractor
import my.noveldokusha.ui.browse.extractor.BaseExtractor
import java.io.File
import java.util.*

val File.extractor: BaseExtractor
    get() = when (extension.lowercase()) {
        "epub" -> EPubExtractor(this)
        /* *** */
        else -> FileExtractor(this)
    }

val File.content: String
    get() = if (isDirectory) listFiles().contentFormatted else "$sizeFormatted, $modifiedDateString"

val Array<File>?.contentFormatted: String
    get() {
        var folders = 0
        var files = 0
        if (this == null) return "Empty Folder"
        this.forEach { file ->
            if (file.isDirectory) folders++
            else if (file.isFile) files++
        }
        return "$folders Folders, $files Files"
    }

val File.modifiedDateString: String
    get() = Date(lastModified()).shortFormatted

val File.sizeFormatted: String
    get() {
        val bytes = this.length()
        return when {
            bytes > 1099511627776 -> "${bytes / 1099511627776} TB"
            bytes > 1073741824 -> "${bytes / 1073741824} GB"
            bytes > 1048576 -> "${bytes / 1048576} MB"
            bytes > 1024 -> "${bytes / 1024} KB"
            else -> "$bytes bytes"
        }
    }


