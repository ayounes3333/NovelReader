package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.TextExtractor
import org.jsoup.nodes.Document

/**
 * Novel main page example:
 * https://freewebnovel.com/re-zero-starting-life-in-another-world.html
 * Chapter url example:
 * https://freewebnovel.com/re-zero-starting-life-in-another-world/chapter-1.html
 */
class FreeWebNovel(networkClient: NetworkClient) : LibRead(networkClient) {
    override val id = "free_web_novel"
    override val nameStrId = R.string.source_name_free_web_novel
    override val baseUrl = "https://freewebnovel.com"
    override val catalogUrl = "$baseUrl/latest-release-novels/1"
    override val urlHasDotHtml = true

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            val content = doc.selectFirst("div.txt") ?: return@withContext ""
            content.selectFirst(".notice-text")?.remove()
            content.select("script, style").remove()
            // Remove freewebnovel-specific promotional paragraphs
            content.select("p").forEach { p ->
                val t = p.text()
                if (t.contains("Freewebnovel", ignoreCase = true) ||
                    t.contains("Libread", ignoreCase = true) ||
                    t.contains("moving Freewebnovel", ignoreCase = true)
                ) p.remove()
            }
            TextExtractor.get(content)
        }

    override suspend fun getCatalogList(index: Int) = withContext(Dispatchers.Default) {
        tryConnect {
            val page = index + 1
            val url = "$baseUrl/latest-release-novels/$page"
            parseBookListFwn(networkClient.get(url).toDocument(), index)
        }
    }

    private fun parseBookListFwn(
        doc: org.jsoup.nodes.Document,
        index: Int,
    ): my.noveldokusha.core.PagedList<my.noveldokusha.scraper.domain.BookResult> {
        val books = doc.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row").mapNotNull { h ->
            val a = h.selectFirst("h3.tit > a") ?: return@mapNotNull null
            my.noveldokusha.scraper.domain.BookResult(
                title = a.attr("title").ifBlank { a.text() },
                url = a.attr("href").ifBlank { return@mapNotNull null },
                coverImageUrl = h.selectFirst("div.pic > a > img")?.attr("src") ?: ""
            )
        }
        val isLastPage = doc.selectFirst("a.next, .pagination .next") == null
        return my.noveldokusha.core.PagedList(list = books, index = index, isLastPage = isLastPage)
    }
}
