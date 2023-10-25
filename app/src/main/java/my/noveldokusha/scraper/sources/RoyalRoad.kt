package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.R
import my.noveldokusha.data.BookMetadata
import my.noveldokusha.data.ChapterMetadata
import my.noveldokusha.data.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.PagedList
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.LanguageCode
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.utils.add
import my.noveldokusha.utils.addPath
import my.noveldokusha.utils.toDocument
import my.noveldokusha.utils.toUrlBuilderSafe
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Novel main page (chapter list) example:
 * https://www.royalroad.com/fiction/21220/mother-of-learning
 * Chapter url example:
 * https://www.royalroad.com/fiction/21220/mother-of-learning/chapter/301778/1-good-morning-brother
 */
class RoyalRoad(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "royal_road"
    override val nameStrId = R.string.source_name_royal_road
    override val baseUrl = "https://www.royalroad.com/"
    override val catalogUrl = "https://www.royalroad.com/fictions/latest-updates?page=1"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".fic-headers h4")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst(".chapter-content")!!.let {
            it.select("script").remove()
            it.select("a").remove()
            it.select(".ads-title").remove()
            it.select(".hidden").remove()
            TextExtractor.get(it)
        }
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".cover-art-container img[src]")
                ?.attr("src")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".description")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterMetadata>> = withContext(Dispatchers.Default) {
        tryConnect {
            var chapterRows = networkClient.get(bookUrl)
                .toDocument()
                .select(".chapter-row");

            chapterRows.select("a[href]")
                .map { ChapterMetadata(title = it.text(), url = URI(baseUrl).resolve(it.attr("href")).toString()) }
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookMetadata>> = withContext(Dispatchers.Default) {
        tryConnect("index=$index") {
            val page = index + 1
            val url = "https://www.royalroad.com/"
                .toUrlBuilderSafe()
                .addPath("fictions", "best-rated")
                .add("page", page.toString())

            val doc = networkClient.get(url).toDocument()
            val fictionListItems = doc.select(".fiction-list-item")
            val pageBooks = fictionListItems
                .mapNotNull {
                    val nullableHref = it.select("a[href]").getOrNull(1);
                    val link = nullableHref ?: return@mapNotNull null
                    val bookCover = it.selectFirst("img[src]")?.attr("src") ?: ""
                    BookMetadata(
                        title = link.text(),
                        url = URI(baseUrl).resolve(link.attr("href")).toString(),
                        coverImageUrl = bookCover
                    )
                };

            PagedList(
                list = pageBooks,
                index = index,
                isLastPage = when (val nav = doc.selectFirst("ul.pagination")) {
                    null -> true
                    else -> nav.children().last()?.`is`(".active") ?: true
                }
            )
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookMetadata>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank() || index > 0)
                return@tryConnect PagedList.createEmpty(index = index)

            val request = getRequest("https://www.royalroad.com/fictions/search?title=${input}")
                .addHeader("accept", "*/*")
                .addHeader("accept-encoding", "gzip, deflate, br")
                .addHeader(
                    "accept-language",
                    "en-GB,en-US;q=0.9,en;q=0.8,ca;q=0.7,es-ES;q=0.6,es;q=0.5,de;q=0.4"
                )
                .addHeader("cache-control", "no-cache")
                .addHeader("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader("origin", "https://www.royalroad.com")
                .addHeader("pragma", "no-cache")
                .addHeader("referer", "https://www.royalroad.com")
                .addHeader("sec-ch-ua-platform", "Windows")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-site", "same-origin")
                .addHeader("x-requested-with", "XMLHttpRequest")

            var fictionListItems = networkClient.call(request)
                .toDocument()
                .select(".fiction-list-item");

            fictionListItems.mapNotNull {
                    val link = it.select("a[href]").getOrNull(1) ?: return@mapNotNull null
                    val bookCover = it.selectFirst("img[src]")?.attr("src") ?: ""
                    BookMetadata(
                        title = link.text(),
                        url =  URI(baseUrl).resolve(link.attr("href")).toString(),
                        coverImageUrl = bookCover
                    )
                }
                .let {
                    PagedList(
                        list = it,
                        index = index,
                        isLastPage = true
                    )
                }
        }
    }
}
