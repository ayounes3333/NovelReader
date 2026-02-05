package my.noveldokusha.scraper.sources

import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.TestUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoyalRoadTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var royalRoad: RoyalRoad
    private lateinit var networkClient: NetworkClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        networkClient = NetworkClient.create()
        royalRoad = RoyalRoad(networkClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getChapterTitle should extract title from document`() = runTest {
        // Arrange
        val mockHtml = """
            <html>
            <body>
                <div class="fic-headers">
                    <h4>Chapter 1: The Beginning</h4>
                </div>
            </body>
            </html>
        """.trimIndent()
        val doc = TestUtils.createDocument(mockHtml)

        // Act
        val title = royalRoad.getChapterTitle(doc)

        // Assert
        assertEquals("Chapter 1: The Beginning", title)
    }

    @Test
    fun `getChapterText should extract and clean chapter content`() = runTest {
        // Arrange
        val mockHtml = """
            <html>
            <head>
                <style>
                    .hidden-class { display: none; }
                </style>
            </head>
            <body>
                <div class="chapter-content">
                    <p>This is the first paragraph.</p>
                    <script>console.log('remove me');</script>
                    <a href="/link">Remove this link</a>
                    <div class="ads-title">Advertisement</div>
                    <div class="hidden">Hidden content</div>
                    <div class="hidden-class">Also hidden</div>
                    <p>This is the second paragraph.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        val doc = TestUtils.createDocument(mockHtml)

        // Act
        val text = royalRoad.getChapterText(doc)

        // Assert
        assertNotNull("Chapter text should not be null", text)
        assertFalse("Should not contain script tags", text.contains("console.log"))
        assertFalse("Should not contain ads", text.contains("Advertisement"))
        assertFalse("Should not contain hidden content", text.contains("Hidden content"))
        assertTrue("Should contain main content", text.contains("first paragraph"))
    }

    @Test
    fun `getCatalogList should return books with pagination info`() = runTest {
        // Arrange
        val mockHtml = createMockRoyalRoadCatalogHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = royalRoad.getCatalogList(0)

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val pagedList = (result as Response.Success).data
        assertTrue("Should have books", pagedList.list.isNotEmpty())
        assertEquals("Should have correct index", 0, pagedList.index)
        assertFalse("Should not be last page", pagedList.isLastPage)
    }

    @Test
    fun `getChapterList should extract chapter URLs and titles`() = runTest {
        // Arrange
        val mockHtml = createMockRoyalRoadBookHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = royalRoad.getChapterList("https://example.com/book/1")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val chapters = (result as Response.Success).data
        assertTrue("Should have chapters", chapters.isNotEmpty())
        chapters.forEach { chapter ->
            assertNotNull("Chapter title should not be null", chapter.title)
            assertTrue("Chapter URL should be valid", chapter.url.startsWith("http"))
        }
    }

    @Test
    fun `getCatalogSearch should handle empty search gracefully`() = runTest {
        // Act
        val result = royalRoad.getCatalogSearch(0, "")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val pagedList = (result as Response.Success).data
        assertTrue("Should return empty list for blank search", pagedList.list.isEmpty())
    }

    @Test
    fun `getCatalogSearch should handle pagination correctly`() = runTest {
        // Act - search beyond first page should return empty
        val result = royalRoad.getCatalogSearch(1, "test query")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val pagedList = (result as Response.Success).data
        assertTrue("Should return empty list for index > 0", pagedList.list.isEmpty())
    }

    @Test
    fun `getBookCoverImageUrl should extract cover URL`() = runTest {
        // Arrange
        val mockHtml = """
            <html>
            <body>
                <div class="cover-art-container">
                    <img src="/book-cover.jpg" alt="Book Cover"/>
                </div>
            </body>
            </html>
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = royalRoad.getBookCoverImageUrl("https://example.com/book/1")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val coverUrl = (result as Response.Success).data
        assertEquals("/book-cover.jpg", coverUrl)
    }

    @Test
    fun `getBookDescription should extract description`() = runTest {
        // Arrange
        val mockHtml = """
            <html>
            <body>
                <div class="description">
                    <p>This is the book description.</p>
                    <p>With multiple paragraphs.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = royalRoad.getBookDescription("https://example.com/book/1")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val description = (result as Response.Success).data
        assertNotNull("Description should not be null", description)
        assertTrue("Should contain description text", description!!.contains("book description"))
    }

    private fun createMockRoyalRoadCatalogHtml(): String {
        return """
            <html>
            <body>
                <div class="fiction-list-item">
                    <a href="/fiction/1/test-fiction-1">Link 1</a>
                    <a href="/fiction/1/test-fiction-1">Test Fiction 1</a>
                    <img src="/cover1.jpg" alt="Cover 1"/>
                </div>
                <div class="fiction-list-item">
                    <a href="/fiction/2/test-fiction-2">Link 2</a>
                    <a href="/fiction/2/test-fiction-2">Test Fiction 2</a>
                    <img src="/cover2.jpg" alt="Cover 2"/>
                </div>
                <ul class="pagination">
                    <li class="active">1</li>
                    <li><a href="?page=2">2</a></li>
                    <li><a href="?page=3">3</a></li>
                </ul>
            </body>
            </html>
        """.trimIndent()
    }

    private fun createMockRoyalRoadBookHtml(): String {
        return """
            <html>
            <body>
                <div class="chapter-row">
                    <a href="/fiction/1/chapter/1">Chapter 1: The Beginning</a>
                </div>
                <div class="chapter-row">
                    <a href="/fiction/1/chapter/2">Chapter 2: The Journey</a>
                </div>
                <div class="chapter-row">
                    <a href="/fiction/1/chapter/3">Chapter 3: The End</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}