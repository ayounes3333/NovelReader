package my.noveldokusha.scraper

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Utility class for creating test data and mock HTML responses for scraper tests
 */
object TestUtils {

    /**
     * Creates sample book results for testing
     */
    fun createSampleBookResults(count: Int = 3): List<BookResult> {
        return (1..count).map { i ->
            BookResult(
                title = "Test Book $i",
                url = "https://example.com/book/$i",
                coverImageUrl = "https://example.com/cover/$i.jpg"
            )
        }
    }

    /**
     * Creates sample chapter results for testing
     */
    fun createSampleChapterResults(count: Int = 5): List<ChapterResult> {
        return (1..count).map { i ->
            ChapterResult(
                title = "Chapter $i: Test Chapter Title",
                url = "https://example.com/chapter/$i"
            )
        }
    }

    /**
     * Creates sample database book data for testing
     */
    fun createSampleBookData(): DatabaseInterface.BookData {
        return DatabaseInterface.BookData(
            title = "Test Novel Title",
            description = "This is a test novel description with some interesting plot details.",
            alternativeTitles = listOf("Alternative Title 1", "Alternative Title 2"),
            authors = listOf(
                DatabaseInterface.AuthorMetadata("Test Author 1", "https://example.com/author/1"),
                DatabaseInterface.AuthorMetadata("Test Author 2", "https://example.com/author/2")
            ),
            tags = listOf("Action", "Adventure", "Fantasy"),
            genres = listOf(
                SearchGenre("1", "Action"),
                SearchGenre("2", "Fantasy"),
                SearchGenre("3", "Adventure")
            ),
            bookType = "Web Novel",
            relatedBooks = createSampleBookResults(2),
            similarRecommended = createSampleBookResults(3),
            coverImageUrl = "https://example.com/cover.jpg"
        )
    }

    /**
     * Creates mock HTML for a catalog page
     */
    fun createMockCatalogHtml(): String {
        return """
            <html>
            <body>
                <div class="book-list">
                    <div class="book-item">
                        <a href="/book/1" class="book-title">Test Book 1</a>
                        <img src="/cover/1.jpg" alt="Cover 1"/>
                    </div>
                    <div class="book-item">
                        <a href="/book/2" class="book-title">Test Book 2</a>
                        <img src="/cover/2.jpg" alt="Cover 2"/>
                    </div>
                    <div class="book-item">
                        <a href="/book/3" class="book-title">Test Book 3</a>
                        <img src="/cover/3.jpg" alt="Cover 3"/>
                    </div>
                </div>
                <div class="pagination">
                    <span class="current">1</span>
                    <a href="?page=2">2</a>
                    <a href="?page=3">3</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Creates mock HTML for a book detail page
     */
    fun createMockBookDetailHtml(): String {
        return """
            <html>
            <body>
                <h1 class="book-title">Test Novel Title</h1>
                <div class="book-cover">
                    <img src="/cover.jpg" alt="Book Cover"/>
                </div>
                <div class="book-description">
                    <p>This is a test novel description with some interesting plot details.</p>
                </div>
                <div class="book-authors">
                    <a href="/author/1">Test Author 1</a>
                    <a href="/author/2">Test Author 2</a>
                </div>
                <div class="book-genres">
                    <span class="genre" data-id="1">Action</span>
                    <span class="genre" data-id="2">Fantasy</span>
                    <span class="genre" data-id="3">Adventure</span>
                </div>
                <div class="book-tags">
                    <span class="tag">Action</span>
                    <span class="tag">Adventure</span>
                    <span class="tag">Fantasy</span>
                </div>
                <div class="chapters-list">
                    <a href="/chapter/1" class="chapter">Chapter 1: The Beginning</a>
                    <a href="/chapter/2" class="chapter">Chapter 2: The Journey</a>
                    <a href="/chapter/3" class="chapter">Chapter 3: The Adventure</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Creates mock HTML for a chapter page
     */
    fun createMockChapterHtml(): String {
        return """
            <html>
            <body>
                <h2 class="chapter-title">Chapter 1: The Beginning</h2>
                <div class="chapter-content">
                    <p>This is the first paragraph of the chapter.</p>
                    <p>This is the second paragraph with more content.</p>
                    <p>And here's the third paragraph to complete the chapter.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Creates a Jsoup Document from HTML string
     */
    fun createDocument(html: String): Document {
        return Jsoup.parse(html)
    }

    /**
     * Creates a test failure response
     */
    fun <T> createErrorResponse(message: String = "Test error"): Response<T> {
        return Response.Error(Exception(message))
    }

    /**
     * Creates a test success response
     */
    fun <T> createSuccessResponse(data: T): Response<T> {
        return Response.Success(data)
    }

    /**
     * Mock implementation of SourceInterface.Catalog for testing
     */
    class MockSourceCatalog(
        override val id: String = "test_source",
        override val nameStrId: Int = 0,
        override val baseUrl: String = "https://test.example.com",
        override val catalogUrl: String = "https://test.example.com/catalog",
        override val language: LanguageCode = LanguageCode.ENGLISH,
        private val mockBooks: List<BookResult> = createSampleBookResults(),
        private val mockChapters: List<ChapterResult> = createSampleChapterResults()
    ) : SourceInterface.Catalog {

        override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> {
            return createSuccessResponse(mockChapters)
        }

        override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> {
            return createSuccessResponse(PagedList(mockBooks, index, isLastPage = index >= 2))
        }

        override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> {
            val filteredBooks = if (input.isBlank()) mockBooks else mockBooks.filter { 
                it.title.contains(input, ignoreCase = true) 
            }
            return createSuccessResponse(PagedList(filteredBooks, index, isLastPage = true))
        }

        override suspend fun getChapterTitle(doc: Document): String? {
            return doc.selectFirst(".chapter-title")?.text()
        }

        override suspend fun getChapterText(doc: Document): String? {
            return doc.selectFirst(".chapter-content")?.text()
        }
    }

    /**
     * Mock implementation of DatabaseInterface for testing
     */
    class MockDatabase(
        override val id: String = "test_database",
        override val nameStrId: Int = 0,
        override val baseUrl: String = "https://testdb.example.com",
        private val mockBooks: List<BookResult> = createSampleBookResults(),
        private val mockBookData: DatabaseInterface.BookData = createSampleBookData(),
        private val mockGenres: List<SearchGenre> = listOf(
            SearchGenre("1", "Action"),
            SearchGenre("2", "Fantasy"),
            SearchGenre("3", "Romance")
        )
    ) : DatabaseInterface {

        override suspend fun getCatalog(index: Int): Response<PagedList<BookResult>> {
            return createSuccessResponse(PagedList(mockBooks, index, isLastPage = index >= 2))
        }

        override suspend fun searchByTitle(index: Int, input: String): Response<PagedList<BookResult>> {
            val filteredBooks = if (input.isBlank()) mockBooks else mockBooks.filter { 
                it.title.contains(input, ignoreCase = true) 
            }
            return createSuccessResponse(PagedList(filteredBooks, index, isLastPage = true))
        }

        override suspend fun searchByFilters(
            index: Int,
            genresIncludedId: List<String>,
            genresExcludedId: List<String>
        ): Response<PagedList<BookResult>> {
            return createSuccessResponse(PagedList(mockBooks, index, isLastPage = true))
        }

        override suspend fun getSearchFilters(): Response<List<SearchGenre>> {
            return createSuccessResponse(mockGenres)
        }

        override suspend fun getBookData(bookUrl: String): Response<DatabaseInterface.BookData> {
            return createSuccessResponse(mockBookData)
        }

        override suspend fun getAuthorData(authorUrl: String): Response<DatabaseInterface.AuthorData> {
            return createSuccessResponse(
                DatabaseInterface.AuthorData(
                    name = "Test Author",
                    books = createSampleBookResults(5),
                    description = "Test author description"
                )
            )
        }
    }
    
    /**
     * Mock implementation of LocalSource for testing
     */
    class MockLocalSource(
        override val id: String = "local_source",
        override val nameStrId: Int = 0,
        override val baseUrl: String = "local://",
        override val isLocalSource: Boolean = true
    ) : SourceInterface.Base {
        override suspend fun getChapterTitle(doc: Document): String? = null
        override suspend fun getChapterText(doc: Document): String? = null
    }
}