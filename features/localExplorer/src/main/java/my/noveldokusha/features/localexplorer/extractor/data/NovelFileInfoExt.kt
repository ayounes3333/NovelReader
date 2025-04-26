package my.noveldokusha.features.localexplorer.extractor.data

import my.noveldokusha.feature.local_database.tables.localexplorer.NovelFileInfo
import my.noveldokusha.features.localexplorer.extractor.utils.sizeFormatted
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val NovelFileInfo.fileSize: String
    get() = File(path).sizeFormatted

val NovelFileInfo.dateFormatted: String
    get() = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(date))