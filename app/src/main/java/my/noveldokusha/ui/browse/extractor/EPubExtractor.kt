package my.noveldokusha.ui.browse.extractor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import my.noveldokusha.ui.browse.extractor.data.ChapterInfo
import my.noveldokusha.ui.browse.extractor.data.Cover
import my.noveldokusha.ui.browse.extractor.utils.BitmapUtils.textAsBitmap
import my.noveldokusha.ui.browse.extractor.utils.XmlParser
import my.noveldokusha.ui.browse.extractor.utils.zip.ArchiveEntry
import my.noveldokusha.ui.browse.extractor.utils.zip.ZipArchiveInputStream
import my.noveldokusha.ui.browse.FileManager
import my.noveldokusha.utils.spToPx
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class EPubExtractor(private val file: File) : BaseExtractor(file) {

    private val epubDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var id: String = ""
    private var title: String = ""
    private var description: String = ""
    private var author: String = ""
    private var date: Long = System.currentTimeMillis()
    private var chapters: MutableList<ChapterInfo> = ArrayList()

    init {
        if (!file.exists() || file.extension.lowercase() != "epub") {
            throw Exception("Extraction Error! File ${file.name} is not a valid epub!")
        } else {
            parseMeta()
        }
    }

    private fun parseMeta() {
        try {
            Log.d("EPubExtractor", "parsing epub meta for path $path")
            val zipInputStream = ZipArchiveInputStream(path)
            var nextEntry: ArchiveEntry?
            while (zipInputStream.nextEntry.also { nextEntry = it } != null) {
                val name: String = nextEntry?.name?.lowercase() ?: ""
                if (name.endsWith(".opf")) {
                    val xpp = XmlParser.buildPullParser()
                    xpp.setInput(zipInputStream, "utf-8")
                    var eventType = xpp.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (xpp.name) {
                                "dc:title", "dcns:title" -> {
                                    title = if (title.isNotEmpty()) {
                                        xpp.nextText()
                                    } else {
                                        title + " - " + xpp.nextText()
                                    }
                                }
                                "dc:identifier", "dcns:identifier" -> {
                                    id = if (id.isEmpty()) {
                                        xpp.nextText()
                                    } else {
                                        id + " - " + xpp.nextText()
                                    }
                                }
                                "dc:creator", "dcns:creator" -> {
                                    author = if (author.isEmpty()) {
                                        xpp.nextText()
                                    } else {
                                        author + " - " + xpp.nextText()
                                    }
                                }
                                "dc:date", "dcns:date" -> {
                                    val dateString = xpp.nextText()
                                    date = if (dateString == null) {
                                        try {
                                            epubDateFormat.parse(dateString)?.time ?: file.lastModified()
                                        } catch(e: ParseException) {
                                            file.lastModified()
                                        }
                                    } else file.lastModified()
                                }
                                "dc:description", "dcns:description" -> {
                                    description = xpp.nextText() + "," + description
                                }
                            }
                        }
                        if (eventType == XmlPullParser.END_TAG) {
                            if ("metadata" == xpp.name) {
                                break
                            }
                        }
                        eventType = xpp.next()
                    }
                }
                else if (name.endsWith(".ncx")) {
                    chapters = ArrayList()
                    val xpp = XmlParser.buildPullParser()
                    xpp.setInput(zipInputStream, "utf-8")
                    var eventType = xpp.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            if ("navPoint" == xpp.name) {
                                var title = ""
                                var content = ""
                                while (eventType != XmlPullParser.END_TAG && xpp.name == "navPoint") {
                                    if ("text" == xpp.name.lowercase()) {
                                        title = xpp.nextText()
                                    }
                                    if ("content" == xpp.name.lowercase()) {
                                        content = xpp.getAttributeValue(null, "src")
                                    }
                                    eventType = xpp.next()
                                }
                                if (title.isNotEmpty() && content.isNotEmpty()) {
                                    chapters.add(ChapterInfo(title, content))
                                }
                            }
                        }
                        if (eventType == XmlPullParser.END_TAG) {
                            if ("navMap" == xpp.name) {
                                break
                            }
                        }
                        eventType = xpp.next()
                    }
                }
            }
            zipInputStream.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun getNovelCover(): Cover {
        val filename = getNovelId() + "-" + getNovelTitle()
        var cover: Bitmap? = FileManager.getCachedNovelCover(filename)
        if (cover == null) {
            try {
                var zipInputStream = ZipArchiveInputStream(path)
                var nextEntry: ArchiveEntry? = null
                var coverName: String? = null
                var coverResource: String? = null
                while (coverName == null && zipInputStream.nextEntry
                        .also { nextEntry = it } != null
                ) {
                    val name: String = nextEntry?.name?.lowercase() ?: ""
                    if (name.endsWith(".opf")) {
                        val xpp: XmlPullParser = XmlParser.buildPullParser()
                        xpp.setInput(zipInputStream, "utf-8")
                        var eventType = xpp.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if ("meta" == xpp.name && "cover" == xpp.getAttributeValue(
                                        null,
                                        "name"
                                    )
                                ) {
                                    coverResource = xpp.getAttributeValue(null, "content")
                                }
                                if ("item" == xpp.name && "cover-image" == xpp.getAttributeValue(
                                        null,
                                        "properties"
                                    )
                                ) {
                                    coverName = xpp.getAttributeValue(null, "href")
                                    if (coverName != null && coverName.endsWith(".svg")) {
                                        coverName = null
                                    }
                                    break
                                }
                                if (coverResource != null && "item" == xpp.name && coverResource == xpp.getAttributeValue(
                                        null,
                                        "id"
                                    )
                                ) {
                                    coverName = xpp.getAttributeValue(null, "href")
                                    if (coverName != null && coverName.endsWith(".svg")) {
                                        coverName = null
                                    }
                                    break
                                }
                            }
                            eventType = xpp.next()
                        }
                    }
                }
                if (coverName != null) {
                    zipInputStream.close()
                    zipInputStream = ZipArchiveInputStream(path)
                    while (zipInputStream.nextEntry.also { nextEntry = it } != null) {
                        val name: String = nextEntry?.name ?: ""
                        if (name.contains(coverName)) {
                            cover = BitmapFactory.decodeStream(zipInputStream)
                            break
                        }
                    }
                }
                if (cover == null) {
                    zipInputStream.close()
                    zipInputStream = ZipArchiveInputStream(path)
                    while (zipInputStream.nextEntry.also { nextEntry = it } != null) {
                        val name: String = nextEntry?.name?.lowercase() ?: ""
                        if (name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".png")) {
                            if (name.contains("cover")) {
                                cover = BitmapFactory.decodeStream(zipInputStream)
                                break
                            }
                        }
                    }
                }
                if (cover == null) {
                    zipInputStream.close()
                    zipInputStream = ZipArchiveInputStream(path)
                    while (zipInputStream.nextEntry?.also { nextEntry = it } != null) {
                        val name: String = nextEntry?.name?.lowercase() ?: ""
                        if (name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".png")) {
                            cover = BitmapFactory.decodeStream(zipInputStream)
                            break
                        }
                    }
                }
                zipInputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (cover != null) FileManager.saveNovelCover(filename, cover)
        }
        return Cover(cover ?: textAsBitmap(getNovelTitle(), 14.spToPx.toFloat()), getNovelTitle())
    }

    override fun getNovelChapters(): List<ChapterInfo> = chapters

    override fun getNovelTitle(): String = title

    override fun getNovelId(): String = id + title

    override fun getNovelAuthor(): String = author

    override fun getNovelDate(): Long = date

    override fun getNovelTags(): List<String> = emptyList()

    override fun getNovelDescription(): String = description
}