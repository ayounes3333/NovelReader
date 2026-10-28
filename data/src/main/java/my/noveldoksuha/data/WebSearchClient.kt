package my.noveldoksuha.data

import android.net.Uri
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.TextExtractor
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.Headers
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * Key-less web search backed by DuckDuckGo's HTML endpoint plus a readable
 * page extractor. Used as tools for the AI assistant; not for scraping novel
 * sources (those live in `scraper`).
 */
@Singleton
class WebSearchClient @Inject constructor(
    private val networkClient: NetworkClient,
) {

    suspend fun search(query: String, maxResults: Int = DEFAULT_MAX_RESULTS): Response<List<WebSearchResult>> =
        tryConnect("DuckDuckGo search: $query") {
            val url = Uri.parse(DDG_HTML_ENDPOINT).buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("kl", "wt-wt")
                .build()
            val document = networkClient
                .call(getRequest(url.toString(), headers = SEARCH_HEADERS), followRedirects = true)
                .toDocument()
            parseResults(document, maxResults)
        }

    /**
     * Fetches [url] and returns its main readable text (title + body),
     * trimmed to [maxChars].
     */
    suspend fun fetchReadable(url: String, maxChars: Int = DEFAULT_PAGE_MAX_CHARS): Response<String> =
        tryConnect("Fetch page: $url") {
            val document = networkClient.get(url).toDocument()
            val article = Readability4JExtended(url, document).parse()
            val body = article.articleContent?.let { TextExtractor.get(it) }
                ?.takeIf { it.isNotBlank() }
                ?: document.body()?.text().orEmpty()
            val title = article.title?.takeIf { it.isNotBlank() } ?: document.title()
            buildString {
                if (title.isNotBlank()) appendLine("# $title").appendLine()
                append(body.trim())
            }.let { if (it.length > maxChars) it.take(maxChars) + "\n[truncated]" else it }
        }

    internal fun parseResults(document: Document, maxResults: Int): List<WebSearchResult> =
        document.select("div.result, div.web-result")
            .mapNotNull { result ->
                val anchor = result.selectFirst("a.result__a") ?: return@mapNotNull null
                val href = resolveRedirect(anchor.attr("href")) ?: return@mapNotNull null
                val title = anchor.text().trim()
                if (title.isEmpty()) return@mapNotNull null
                WebSearchResult(
                    title = title,
                    url = href,
                    snippet = result.selectFirst(".result__snippet")?.text()?.trim().orEmpty(),
                )
            }
            .distinctBy { it.url }
            .take(maxResults)

    /** DuckDuckGo wraps outbound links as `//duckduckgo.com/l/?uddg=<encoded>`. */
    private fun resolveRedirect(href: String): String? {
        if (href.isBlank()) return null
        val absolute = if (href.startsWith("//")) "https:$href" else href
        val uri = runCatching { Uri.parse(absolute) }.getOrNull() ?: return null
        val target = uri.getQueryParameter("uddg")
        val resolved = target ?: absolute
        return resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private companion object {
        const val DDG_HTML_ENDPOINT = "https://html.duckduckgo.com/html/"
        const val DEFAULT_MAX_RESULTS = 6
        const val DEFAULT_PAGE_MAX_CHARS = 12_000

        val SEARCH_HEADERS: Headers = Headers.Builder()
            .add("Accept", "text/html")
            .add("Accept-Language", "en-US,en;q=0.9")
            .build()
    }
}
