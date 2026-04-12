package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
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
 * Machine-translated novels.
 * Novel main page example:
 * https://mtlnovel.me/my-vampire-system/
 * Chapter url example:
 * https://mtlnovel.me/my-vampire-system/chapter-1/
 *
 * NOTE: Different from MTLNovel.kt which uses mtlnovel.com.
 */
class MtlNovelsMe(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "mtlnovel_me"
    override val nameStrId = R.string.source_name_mtlnovel_me
    override val baseUrl = "https://mtlnovel.me"
    override val catalogUrl = "$baseUrl/list/?page=1"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1, .chapter-title, h2.title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("div.content.text-break, div.chapter-content")
                ?.let {
                    it.select("script, style, .ads, .ad-wrap").remove()
                    TextExtractor.get(it)
                } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.content-main-image img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.m-card.text-break")
                    ?.ownText()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val doc = networkClient.get(bookUrl).toDocument()
                // mtlnovel.me embeds the chapter-list slug in a script tag via ?slug=
                val slug = Regex("\\?slug=([^'\"]+)")
                    .find(doc.toString())
                    ?.groupValues?.get(1)
                    ?: return@tryConnect emptyList()

                networkClient.get("$baseUrl/ajax/chapters/?slug=$slug")
                    .toDocument()
                    .select("p.update-box-chapter")
                    .mapNotNull { c ->
                        val href = c.selectFirst("a")?.attr("href")
                            ?: return@mapNotNull null
                        ChapterResult(title = c.text().ifBlank { "Chapter" }, url = href)
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = "$baseUrl/list/?page=$page"
                val doc = networkClient.get(url).toDocument()
                val books = doc.select("div.novel-box").mapNotNull { h ->
                    BookResult(
                        title = h.selectFirst("h3")?.text() ?: return@mapNotNull null,
                        url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                        coverImageUrl = h.selectFirst("img")?.attr("src") ?: ""
                    )
                }
                val isLastPage = doc.selectFirst(".next, a[rel=next]") == null
                PagedList(list = books, index = index, isLastPage = isLastPage)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (index > 0) return@tryConnect PagedList.createEmpty(index)
                val doc = networkClient.get("$baseUrl/search/?keyword=$input").toDocument()
                val books = doc.select("div.novel-box").mapNotNull { h ->
                    BookResult(
                        title = h.selectFirst("h3")?.text() ?: return@mapNotNull null,
                        url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                        coverImageUrl = h.selectFirst("img")?.attr("src") ?: ""
                    )
                }
                PagedList(list = books, index = index, isLastPage = true)
            }
        }
}
