package my.noveldokusha.features.localexplorer.extractor.utils

import my.noveldokusha.features.localexplorer.R
import my.noveldokusha.features.localexplorer.extractor.EPubExtractor
import my.noveldokusha.features.localexplorer.extractor.FileExtractor
import my.noveldokusha.features.localexplorer.extractor.BaseExtractor
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
val File.vectorIcon: Int
    get() {
        return if (isDirectory) {
            if (listFiles().isNullOrEmpty()) R.drawable.folder_empty else R.drawable.folder
        } else when (extension.lowercase()) {
            "ai" -> R.drawable.ai
            "avi" -> R.drawable.avi
            "css" -> R.drawable.css
            "csv" -> R.drawable.csv
            "dbf" -> R.drawable.dbf
            "doc", "docx" -> R.drawable.doc
            "dwg" -> R.drawable.dwg
            "exe", "com" -> R.drawable.exe
            "fla" -> R.drawable.fla
            "html" -> R.drawable.html
            "iso" -> R.drawable.iso
            "js", "jsx" -> R.drawable.javascript
            "jpg", "jpeg" -> R.drawable.jpg
            "json" -> R.drawable.json_file
            "mp3" -> R.drawable.mp3
            "mp4" -> R.drawable.mp4
            "pdf" -> R.drawable.pdf
            "png" -> R.drawable.png
            "ppt", "pptx" -> R.drawable.ppt
            "psd" -> R.drawable.psd
            "rtf" -> R.drawable.rtf
            "svg" -> R.drawable.svg
            "txt" -> R.drawable.txt
            "xls", "xlsx" -> R.drawable.xls
            "xml" -> R.drawable.xml
            "zip" -> R.drawable.zip
            "7z", "rar", "gz", "tar", "bz" -> R.drawable.zip_1
            else -> R.drawable.file
        }
    }


