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
 * https://novelsonline.org/tensei-shitara-slime-datta-ken
 *
 * NOTE: This site uses CloudFlare protection. Fetching may fail without a browser-based
 * session. It will work if the user has previously visited the site and the CF cookie
 * is stored by the app's cookie jar.
 */
class NovelsOnline(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "novels_online"
    override val nameStrId = R.string.source_name_novels_online
    override val baseUrl = "https://novelsonline.org"
    override val catalogUrl = "$baseUrl/top-novel/1"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1, h2, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("#contentall")?.let {
                it.select("script, style").remove()
                TextExtractor.get(it)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.novel-left > div.novel-cover > a > img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.novel-right > div > div:nth-child(1) > div.novel-detail-body")
                    ?.text()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .select("ul.chapter-chs > li > a")
                    .map { a ->
                        ChapterResult(
                            title = a.text().ifBlank { "Chapter" },
                            url = a.attr("href")
                        )
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = "$baseUrl/top-novel/$page"
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (index > 0) return@tryConnect PagedList.createEmpty(index)
                val request = postRequest("$baseUrl/sResults.php")
                    .postPayload { add("q", input) }
                val doc = networkClient.call(request).toDocument()
                val books = doc.select("li").mapNotNull { h ->
                    val a = h.selectFirst("a") ?: return@mapNotNull null
                    BookResult(
                        title = h.text().ifBlank { return@mapNotNull null },
                        url = a.attr("href").ifBlank { return@mapNotNull null },
                        coverImageUrl = h.selectFirst("img")?.attr("src") ?: ""
                    )
                }
                PagedList(list = books, index = index, isLastPage = true)
            }
        }

    private fun parseResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("div.top-novel-block").mapNotNull { h ->
            val a = h.selectFirst("div.top-novel-header > h2 > a") ?: return@mapNotNull null
            BookResult(
                title = a.text().ifBlank { return@mapNotNull null },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = h.selectFirst("div.top-novel-content > div.top-novel-cover > a > img")
                    ?.attr("src") ?: ""
            )
        }
        val isLastPage = doc.selectFirst(".pagination .next, a.next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
