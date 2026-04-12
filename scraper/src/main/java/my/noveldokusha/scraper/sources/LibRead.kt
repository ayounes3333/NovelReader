package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

/**
 * Novel main page example:
 * https://libread.com/libread/lord-of-the-mysteries
 * Chapter url example:
 * https://libread.com/libread/lord-of-the-mysteries/chapter-1
 */
open class LibRead(val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "libread"
    override val nameStrId = R.string.source_name_libread
    override val baseUrl = "https://libread.com"
    override val catalogUrl = "$baseUrl/sort/latest-release/1"
    override val language = LanguageCode.ENGLISH

    // FreeWebNovel appends .html to novel URLs; set true in subclass to strip it before building chapter URL prefix
    open val urlHasDotHtml = false

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1, h2, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            val content = doc.selectFirst("div.txt") ?: return@withContext ""
            content.selectFirst(".notice-text")?.remove()
            content.select("script, style").remove()
            TextExtractor.get(content)
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.pic > a > img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.txt.for-pc, div.inner")
                    ?.let { TextExtractor.get(it) }
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val trimmed = bookUrl.trim().removeSuffix("/")
                val responseBody = networkClient.get(trimmed).body?.string()
                    ?: return@tryConnect emptyList()

                // Extract the numeric aid from an image URL pattern like "12345s.jpg"
                val aid = Regex("[0-9]+s\\.jpg").find(responseBody)
                    ?.value?.substringBefore("s")
                    ?: return@tryConnect emptyList()

                val chapterListRequest = postRequest("$baseUrl/api/chapterlist.php")
                    .postPayload { add("aid", aid) }

                val chaptersDoc = networkClient.call(chapterListRequest).toDocument()
                val prefix = if (urlHasDotHtml) trimmed.removeSuffix(".html") else trimmed

                chaptersDoc.select("select > option").map { opt ->
                    val relUrl = opt.attr("value")
                    ChapterResult(
                        title = opt.text().ifBlank { "Chapter" },
                        url = "$prefix$relUrl"
                    )
                } // options are already ordered oldest → newest
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = "$baseUrl/sort/latest-release/$page"
                parseBookList(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (index > 0) return@tryConnect PagedList.createEmpty(index)
                val request = postRequest("$baseUrl/search")
                    .addHeader("referer", baseUrl)
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .addHeader("content-type", "application/x-www-form-urlencoded")
                    .postPayload { add("searchkey", input) }

                val doc = networkClient.call(request).toDocument()
                val books = doc.select("div.li-row > div.li > div.con").mapNotNull { h ->
                    val a = h.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null
                    BookResult(
                        title = a.attr("title").ifBlank { a.text() },
                        url = a.attr("href").ifBlank { return@mapNotNull null },
                        coverImageUrl = h.selectFirst("div.pic img")?.attr("src") ?: ""
                    )
                }
                PagedList(list = books, index = index, isLastPage = true)
            }
        }

    private fun parseBookList(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row").mapNotNull { h ->
            val a = h.selectFirst("h3.tit > a") ?: return@mapNotNull null
            BookResult(
                title = a.attr("title").ifBlank { a.text() },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = h.selectFirst("div.pic > a > img")?.attr("src") ?: ""
            )
        }
        val isLastPage = doc.selectFirst("a.next, .pagination .next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
