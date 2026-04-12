package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
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
 * https://pawread.com/fantasy/novel-name.html
 * Chapter url example:
 * https://pawread.com/fantasy/novel-name/12345.html
 */
class PawRead(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "pawread"
    override val nameStrId = R.string.source_name_pawread
    override val baseUrl = "https://pawread.com"
    override val catalogUrl = "$baseUrl/list/all-All/update/"
    override val language = LanguageCode.ENGLISH

    private val chapterRegex = Regex("'(\\d+)'")

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1, h2, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            if (doc.selectFirst("#countdown") != null) return@withContext ""
            doc.selectFirst("#chapter_item")?.let {
                it.select("script, style, .ads").remove()
                TextExtractor.get(it)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val doc = networkClient.get(bookUrl).toDocument()
                val board = doc.selectFirst("#tab1_board") ?: return@tryConnect null
                val style = board.selectFirst(">.col-md-3 > div")?.attr("style") ?: ""
                Regex("image:url\\((.*)\\)").find(style)?.groupValues?.get(1)
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("#simple-des")
                    ?.text()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val doc = networkClient.get(bookUrl).toDocument()
                val prefix = "${bookUrl.trimEnd('/')}/"
                doc.select(".item-box")
                    .filter { it.selectFirst("div > svg") == null } // exclude locked chapters
                    .mapNotNull { el ->
                        val title = el.selectFirst("div > span.c_title")?.text()
                            ?: return@mapNotNull null
                        val matchId = chapterRegex.find(el.attr("onclick"))
                            ?.groupValues?.get(1)
                            ?: return@mapNotNull null
                        ChapterResult(title = title, url = "${prefix}${matchId}.html")
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = "$baseUrl/list/all-All/update/?page=$page"
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = "$baseUrl/search/?keywords=$input&page=$page"
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    private fun parseResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select(".list-comic-thumbnail").mapNotNull { el ->
            val a = el.selectFirst(".caption > h3 > a") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            BookResult(
                title = a.text().ifBlank { return@mapNotNull null },
                url = href,
                coverImageUrl = el.selectFirst(".image-link > img")?.attr("src") ?: ""
            )
        }
        val isLastPage = doc.selectFirst(".pagination .next, a.next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
