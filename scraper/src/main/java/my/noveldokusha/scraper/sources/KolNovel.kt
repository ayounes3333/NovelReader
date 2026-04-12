package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.ifCase
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

/**
 * Arabic Web Novel source.
 * Novel main page example:
 * https://kolnovel.com/series/novel-name/
 */
class KolNovel(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "kol_novel"
    override val nameStrId = R.string.source_name_kol_novel
    override val baseUrl = "https://kolnovel.com"
    override val catalogUrl = "$baseUrl/series/?page=1"
    override val language = LanguageCode.ARABIC

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1.entry-title, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("div.entry-content")?.let {
                it.select("script, style, .ads, .sharedaddy, .wp-block-buttons").remove()
                TextExtractor.get(it)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.thumb > img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.entry-content p, div.summary__content")
                    ?.text()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .select("li[data-id] > a")
                    .mapNotNull { a ->
                        val href = a.attr("href").ifBlank { return@mapNotNull null }
                        val num = a.selectFirst("div.epl-num")?.text() ?: ""
                        val title = a.selectFirst("div.epl-title")?.text() ?: ""
                        val name = buildString {
                            if (num.isNotBlank()) append(num)
                            if (num.isNotBlank() && title.isNotBlank()) append(": ")
                            if (title.isNotBlank()) append(title)
                            if (isEmpty()) append("Chapter")
                        }
                        ChapterResult(title = name, url = href)
                    }
                    .reversed() // Oldest first
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .addPath("series")
                    .add("page", page.toString())
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .add("s", input)
                    .ifCase(page > 1) { addPath("page", page.toString()) }
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    private fun parseResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("div.bsx").mapNotNull { h ->
            val a = h.selectFirst("a.tip") ?: return@mapNotNull null
            val href = a.attr("abs:href").ifBlank { a.attr("href") }.ifBlank { return@mapNotNull null }
            val title = a.selectFirst("div.tt span.ntitle")?.text() ?: return@mapNotNull null
            val cover = a.selectFirst("div.limit img")?.attr("src") ?: ""
            BookResult(title = title, url = href, coverImageUrl = cover)
        }
        val isLastPage = doc.selectFirst(".navigation .nav-next, a.next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
