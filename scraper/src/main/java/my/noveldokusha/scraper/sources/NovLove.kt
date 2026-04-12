package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.ifCase
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import okhttp3.Headers
import org.jsoup.nodes.Document

/**
 * NovLove — same site structure as NovelBin.
 * Novel main page example:
 * https://novlove.com/book-name/
 */
class NovLove(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "novlove"
    override val nameStrId = R.string.source_name_novlove
    override val baseUrl = "https://novlove.com"
    override val catalogUrl = "$baseUrl/sort/novlove-daily-update"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h2 > .title-chapter")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".container .adsads, #chr-content, .chapter-c")?.let {
                it.select("script, style, .ads").remove()
                TextExtractor.get(it)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("meta[itemprop=image]")
                    ?.attr("content")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.desc-text")
                    ?.text()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val keyId = networkClient.get(bookUrl).toDocument()
                    .selectFirst("meta[property=og:url]")
                    ?.attr("content")
                    ?.toUrlBuilderSafe()
                    ?.build()
                    ?.lastPathSegment
                    ?: return@tryConnect emptyList()

                val request = getRequest(
                    url = baseUrl.toUrlBuilderSafe()
                        .addPath("ajax", "chapter-archive")
                        .add("novelId" to keyId)
                        .toString(),
                    headers = Headers.Builder()
                        .add("Accept", "*/*")
                        .add("X-Requested-With", "XMLHttpRequest")
                        .add("Referer", "$bookUrl#tab-chapters-title")
                        .build()
                )
                networkClient.call(request).toDocument()
                    .select("ul.list-chapter li a")
                    .map { a ->
                        ChapterResult(
                            title = a.attr("title").ifBlank { a.text() },
                            url = a.attr("href")
                        )
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = catalogUrl.toUrlBuilderSafe()
                    .ifCase(page > 1) { add("page", page.toString()) }
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .addPath("search")
                    .add("keyword" to input)
                    .ifCase(page > 1) { add("page", page.toString()) }
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    private fun parseResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("#list-page div.list-novel .row").mapNotNull { el ->
            val a = el.selectFirst("div.col-xs-7 a") ?: return@mapNotNull null
            BookResult(
                title = a.attr("title").ifBlank { a.text() },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = el.selectFirst("div.col-xs-3 > div > img")?.attr("data-src") ?: ""
            )
        }
        val isLastPage = doc.select("ul.pagination li.next.disabled").isNotEmpty()
            || doc.selectFirst("ul.pagination li.next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
