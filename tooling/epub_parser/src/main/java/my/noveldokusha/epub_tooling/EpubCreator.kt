package my.noveldokusha.epub_tooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal EPUB 2 creator.
 *
 * Produces a standards-compliant EPUB 2 file from a list of plaintext chapters.
 * No external libraries required — uses only the Java standard library ZIP support.
 *
 * Usage:
 * ```kotlin
 * EpubCreator.write(
 *     outputStream = fileOutputStream,
 *     title = "My Novel",
 *     author = "Author Name",
 *     description = "A great story",
 *     coverImageBytes = null,
 *     chapters = listOf(
 *         EpubCreator.Chapter("Chapter 1: Beginning", "Once upon a time..."),
 *         EpubCreator.Chapter("Chapter 2: Middle", "Things happened...")
 *     )
 * )
 * ```
 */
object EpubCreator {

    data class Chapter(val title: String, val body: String)

    /**
     * Writes an EPUB file to [outputStream].
     * The caller is responsible for closing the stream.
     *
     * @param outputStream  Destination stream for the epub bytes.
     * @param title         Book title.
     * @param author        Author name, or null.
     * @param description   Book description / synopsis, or null.
     * @param coverImageBytes  Raw bytes of the cover image (JPEG or PNG), or null.
     * @param chapters      List of chapters with title and plaintext body.
     */
    suspend fun write(
        outputStream: OutputStream,
        title: String,
        author: String?,
        description: String?,
        coverImageBytes: ByteArray?,
        chapters: List<Chapter>,
    ) = withContext(Dispatchers.IO) {
        val bookId = UUID.randomUUID().toString()
        ZipOutputStream(outputStream).use { zip ->
            // The mimetype entry MUST be first and MUST be uncompressed (STORED)
            writeMimetype(zip)
            writeContainerXml(zip)
            writeContentOpf(zip, bookId, title, author, description, coverImageBytes, chapters)
            writeTocNcx(zip, bookId, title, chapters)
            writeCss(zip)
            if (coverImageBytes != null) writeCoverImage(zip, coverImageBytes)
            writeCoverPage(zip, title, author)
            chapters.forEachIndexed { index, chapter ->
                writeChapter(zip, index, chapter)
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun writeMimetype(zip: ZipOutputStream) {
        val data = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().also { it.update(data) }.value
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun writeContainerXml(zip: ZipOutputStream) {
        write(
            zip, "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0"
    xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf"
        media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
        )
    }

    private fun writeContentOpf(
        zip: ZipOutputStream,
        bookId: String,
        title: String,
        author: String?,
        description: String?,
        cover: ByteArray?,
        chapters: List<Chapter>,
    ) {
        val hasCover = cover != null
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf"
         unique-identifier="bookid" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:identifier id="bookid">urn:uuid:$bookId</dc:identifier>
    <dc:title>${title.xmlEscape()}</dc:title>
"""
        )
        if (author != null) sb.append("    <dc:creator>${author.xmlEscape()}</dc:creator>\n")
        if (description != null) sb.append("    <dc:description>${description.xmlEscape()}</dc:description>\n")
        sb.append("    <dc:language>en</dc:language>\n")
        if (hasCover) sb.append("    <meta name=\"cover\" content=\"cover-image\"/>\n")
        sb.append("  </metadata>\n  <manifest>\n")
        sb.append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        sb.append("    <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
        sb.append("    <item id=\"cover-page\" href=\"cover.html\" media-type=\"application/xhtml+xml\"/>\n")
        if (hasCover) sb.append("    <item id=\"cover-image\" href=\"images/cover.jpg\" media-type=\"image/jpeg\"/>\n")
        chapters.forEachIndexed { i, _ ->
            val id = chapterId(i)
            sb.append("    <item id=\"$id\" href=\"chapters/$id.html\" media-type=\"application/xhtml+xml\"/>\n")
        }
        sb.append("  </manifest>\n  <spine toc=\"ncx\">\n")
        sb.append("    <itemref idref=\"cover-page\"/>\n")
        chapters.forEachIndexed { i, _ ->
            sb.append("    <itemref idref=\"${chapterId(i)}\"/>\n")
        }
        sb.append("  </spine>\n</package>")
        write(zip, "OEBPS/content.opf", sb.toString())
    }

    private fun writeTocNcx(
        zip: ZipOutputStream,
        bookId: String,
        title: String,
        chapters: List<Chapter>,
    ) {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
    "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:$bookId"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>${title.xmlEscape()}</text></docTitle>
  <navMap>
    <navPoint id="cover" playOrder="0">
      <navLabel><text>Cover</text></navLabel>
      <content src="cover.html"/>
    </navPoint>
"""
        )
        chapters.forEachIndexed { i, ch ->
            val id = chapterId(i)
            sb.append(
                """    <navPoint id="$id" playOrder="${i + 1}">
      <navLabel><text>${ch.title.xmlEscape()}</text></navLabel>
      <content src="chapters/$id.html"/>
    </navPoint>
"""
            )
        }
        sb.append("  </navMap>\n</ncx>")
        write(zip, "OEBPS/toc.ncx", sb.toString())
    }

    private fun writeCss(zip: ZipOutputStream) {
        write(
            zip, "OEBPS/style.css",
            """body {
  font-family: serif;
  margin: 1em;
  line-height: 1.6;
}
h1, h2 { text-align: center; margin-bottom: 1em; }
p  { text-indent: 1.5em; margin: 0.2em 0; }
.cover-img { width: 100%; height: auto; display: block; margin: 0 auto; }
"""
        )
    }

    private fun writeCoverPage(zip: ZipOutputStream, title: String, author: String?) {
        val imgTag = "<img class=\"cover-img\" src=\"images/cover.jpg\" alt=\"Cover\"/>"
        val authorLine = if (author != null) "<p>${author.xmlEscape()}</p>" else ""
        write(
            zip, "OEBPS/cover.html",
            xhtml(
                title, """
  <div style="text-align:center; margin-top: 3em;">
    $imgTag
    <h1>${title.xmlEscape()}</h1>
    $authorLine
  </div>"""
            )
        )
    }

    private fun writeCoverImage(zip: ZipOutputStream, bytes: ByteArray) {
        val entry = ZipEntry("OEBPS/images/cover.jpg")
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeChapter(zip: ZipOutputStream, index: Int, chapter: Chapter) {
        val id = chapterId(index)
        val paragraphs = chapter.body
            .split("\n")
            .filter { it.isNotBlank() }
            .joinToString("\n") { "  <p>${it.trim().xmlEscape()}</p>" }

        write(
            zip, "OEBPS/chapters/$id.html",
            xhtml(
                chapter.title,
                "\n  <h2>${chapter.title.xmlEscape()}</h2>\n$paragraphs\n"
            )
        )
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private fun chapterId(index: Int) = "ch_${index.toString().padStart(5, '0')}"

    private fun xhtml(title: String, body: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
    "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <title>${title.xmlEscape()}</title>
  <link rel="stylesheet" type="text/css" href="../style.css"/>
</head>
<body>
$body
</body>
</html>"""

    private fun write(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun String.xmlEscape(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
