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
 * WordPress Madara-based translation site.
 * Novel main page example:
 * https://hiraethtranslation.com/novel/novel-name/
 * Chapter url example:
 * https://hiraethtranslation.com/novel/novel-name/chapter-1/
 */
class HiraethTranslation(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "hiraeth_translation"
    override val nameStrId = R.string.source_name_hiraeth_translation
    override val baseUrl = "https://hiraethtranslation.com"
    override val catalogUrl = "$baseUrl/?s&post_type=wp-manga&m_orderby=latest"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".reading-content h1, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("div.text-left, .reading-content .text-left")?.let {
                it.select("script, style, .ads").remove()
                TextExtractor.get(it)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.c-image-hover > a > img, div.summary_image img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .selectFirst("div.summary__content")
                    ?.let { TextExtractor.get(it) }
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument()
                    .select("ul.main.version-chap > li.wp-manga-chapter.free-chap > a")
                    .reversed()
                    .mapNotNull { a ->
                        ChapterResult(
                            title = a.text().ifBlank { "Chapter" },
                            url = a.attr("href").ifBlank { return@mapNotNull null }
                        )
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .ifCase(page > 1) { addPath("page", page.toString()) }
                    .add("s", "")
                    .add("post_type", "wp-manga")
                    .add("m_orderby", "latest")
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .ifCase(page > 1) { addPath("page", page.toString()) }
                    .add("s", input)
                    .add("post_type", "wp-manga")
                    .add("m_orderby", "latest")
                    .toString()
                parseResults(networkClient.get(url).toDocument(), index)
            }
        }

    private fun parseResults(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select(
            "div.c-tabs-item > div.row.c-tabs-item__content, " +
                "div.c-image-hover"
        ).mapNotNull { h ->
            val a = h.selectFirst("h3.h4 > a, .post-title h3 > a") ?: return@mapNotNull null
            BookResult(
                title = a.text().ifBlank { return@mapNotNull null },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = h.selectFirst("img[src]")?.attr("src") ?: ""
            )
        }
        val isLastPage = doc.selectFirst(".nav-previous, .pagination .next") == null
        return PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
