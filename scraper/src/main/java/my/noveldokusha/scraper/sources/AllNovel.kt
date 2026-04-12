package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
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
 * Novel main page example:
 * https://allnovel.org/reincarnated-as-a-slime.html
 * Chapter url example:
 * https://allnovel.org/reincarnated-as-a-slime/chapter-1.html
 */
open class AllNovel(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "allnovel"
    override val nameStrId = R.string.source_name_allnovel
    override val baseUrl = "https://allnovel.org"
    override val catalogUrl = "$baseUrl/hot-novel?page=1"
    override val language = LanguageCode.ENGLISH

    // AJAX endpoint path used by AllNovel clones; can be overridden
    open val ajaxChapterPath = "ajax-chapter-option"

    // Path used for catalog browsing; can be overridden by subclasses
    open val mainCatalogPath = "hot-novel"

    private fun fixUrl(href: String): String =
        if (href.startsWith("http")) href else "$baseUrl$href"

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title, h2.title-chapter")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            val content = doc.selectFirst("#chapter-content")
                ?: doc.selectFirst("#chr-content")
                ?: return@withContext ""
            content.select("script, style, iframe, .ads, #content-ad, .novel-ad").remove()
            // Remove promotional text injected by these sites
            content.select("p").forEach { p ->
                val t = p.text()
                if (t.contains("find any errors") ||
                    t.contains("report chapter") ||
                    t.contains("Updated from F r e e w e b") ||
                    t.contains("source of this content")
                ) p.remove()
            }
            TextExtractor.get(content)
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.book img")
                    ?.let { it.attr("src").takeIf { s -> s.isNotBlank() } ?: it.attr("data-src") }
                    ?.let { fixUrl(it) }
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
                val doc = networkClient.get(bookUrl).toDocument()
                val novelId = doc.selectFirst("#rating")?.attr("data-novel-id")
                    ?: return@tryConnect emptyList()

                val ajaxDoc = networkClient
                    .get("$baseUrl/$ajaxChapterPath?novelId=$novelId")
                    .toDocument()

                var options = ajaxDoc.select("select > option")
                if (options.isEmpty()) options = ajaxDoc.select(".list-chapter > li > a")

                options.mapNotNull { el ->
                    val url = (el.attr("value").takeIf { it.isNotBlank() }
                        ?: el.attr("href")).takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val title = el.text().ifBlank { "Chapter" }
                    ChapterResult(title = title, url = fixUrl(url))
                }.reversed() // site lists newest first; we need oldest first
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .addPath(mainCatalogPath)
                    .add("page", page.toString())
                    .toString()
                parseBookList(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (index > 0) return@tryConnect PagedList.createEmpty(index)
                val searchUrl = "$baseUrl/search?keyword=$input"
                val doc = networkClient.get(searchUrl).toDocument()
                val books = doc.select("#list-page .archive .list .row").mapNotNull { h ->
                    val a = h.selectFirst("> div > div > .truyen-title > a")
                        ?: h.selectFirst("> div > div > .novel-title > a")
                        ?: return@mapNotNull null
                    val cover = h.selectFirst("> div > div > img")?.attr("src")
                    BookResult(
                        title = a.text(),
                        url = fixUrl(a.attr("href")),
                        coverImageUrl = cover?.let { fixUrl(it) } ?: ""
                    )
                }
                PagedList(list = books, index = index, isLastPage = true)
            }
        }

    private fun parseBookList(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("div.list > div.row").mapNotNull { element ->
            val a = element.selectFirst("div > div > h3.truyen-title > a")
                ?: element.selectFirst("div > div > h3.novel-title > a")
                ?: return@mapNotNull null
            val cover = element.selectFirst("div > div > img")?.attr("src")
            BookResult(
                title = a.text(),
                url = fixUrl(a.attr("href")),
                coverImageUrl = cover?.let { fixUrl(it) } ?: ""
            )
        }
        val isLastPage = doc.selectFirst("ul.pagination li.last") == null
            && doc.selectFirst("ul.pagination li.next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
