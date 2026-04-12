package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
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
 * https://www.scribblehub.com/series/11/the-broken-galaxy/
 * Chapter url example:
 * https://www.scribblehub.com/read/11-the-broken-galaxy/chapter/1/
 */
class Scribblehub(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "scribblehub"
    override val nameStrId = R.string.source_name_scribblehub
    override val baseUrl = "https://www.scribblehub.com"
    override val catalogUrl = "$baseUrl/latest/"
    override val language = LanguageCode.ENGLISH

    private val novelIdRegex = Regex("series/([0-9]+)/")

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title, h3.chapter-title, .wi_fic_title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("div#chp_raw")?.let { TextExtractor.get(it) } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.fic_image > img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.wi_fic_desc")
                    ?.text()
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val id = novelIdRegex.find(bookUrl)?.groupValues?.get(1)
                    ?: return@tryConnect emptyList()

                // Request all chapters in ascending order via WP AJAX.
                // The Cookie header sets toc_show (number of entries) and toc_sorder (sort order).
                val request = postRequest("$baseUrl/wp-admin/admin-ajax.php")
                    .addHeader("Referer", bookUrl)
                    .addHeader("Cookie", "toc_show=50000; toc_sorder=asc")
                    .postPayload {
                        add("action", "wi_getreleases_pagination")
                        add("pagenum", "1")
                        add("mypostid", id)
                    }

                networkClient.call(request).toDocument()
                    .select("ol.toc_ol > li")
                    .mapIndexedNotNull { index, el ->
                        val a = el.selectFirst("> a") ?: return@mapIndexedNotNull null
                        val href = a.attr("href").ifBlank { return@mapIndexedNotNull null }
                        val title = a.ownText().ifBlank { "Chapter $index" }
                        ChapterResult(title = title, url = href)
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .addPath("latest")
                    .add("pg", page.toString())
                    .toString()
                parseSearchResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .add("s", input)
                    .add("post_type", "fictionposts")
                    .add("pg", page.toString())
                    .toString()
                parseSearchResults(networkClient.get(url).toDocument(), index)
            }
        }

    private fun parseSearchResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("div.search_main_box, .novel-item").mapNotNull { item ->
            val imgEl = item.selectFirst("div.search_img > img, img.novel-cover")
            val cover = imgEl?.attr("src") ?: ""
            val a = item.selectFirst("div.search_body > div.search_title > a, .novel-title a")
                ?: return@mapNotNull null
            BookResult(
                title = a.text().ifBlank { return@mapNotNull null },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = cover
            )
        }
        val isLastPage = doc.selectFirst(".pagination .next, a[rel=next]") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
