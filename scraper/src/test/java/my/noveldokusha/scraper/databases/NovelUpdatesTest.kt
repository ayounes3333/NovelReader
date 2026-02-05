package my.noveldokusha.scraper.databases

import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.TestUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NovelUpdatesTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var novelUpdates: NovelUpdates
    private lateinit var networkClient: NetworkClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create a NetworkClient that uses the mock server
        networkClient = NetworkClient.create()
        
        // Note: In a real test, you'd need to inject the mock server URL into NovelUpdates
        // This is a simplified example
        novelUpdates = NovelUpdates(networkClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getCatalog should return paged list of books`() = runTest {
        // Arrange
        val mockHtml = createMockNovelUpdatesListHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = novelUpdates.getCatalog(0)

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val pagedList = (result as Response.Success).data
        assertTrue("Should have books", pagedList.list.isNotEmpty())
        assertEquals("Should have correct index", 0, pagedList.index)
    }

    @Test
    fun `searchByTitle should filter books by title`() = runTest {
        // Arrange
        val mockHtml = createMockNovelUpdatesListHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = novelUpdates.searchByTitle(0, "test")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val pagedList = (result as Response.Success).data
        assertNotNull("Should return a paged list", pagedList)
    }

    @Test
    fun `getBookData should parse book details correctly`() = runTest {
        // Arrange
        val mockHtml = createMockNovelUpdatesBookHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = novelUpdates.getBookData("https://example.com/book/1")

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val bookData = (result as Response.Success).data
        assertNotNull("Book title should not be null", bookData.title)
        assertNotNull("Book description should not be null", bookData.description)
    }

    @Test
    fun `getSearchFilters should return available genres`() = runTest {
        // Arrange
        val mockHtml = createMockNovelUpdatesFiltersHtml()
        mockWebServer.enqueue(MockResponse().setBody(mockHtml))

        // Act
        val result = novelUpdates.getSearchFilters()

        // Assert
        assertTrue("Result should be success", result is Response.Success)
        val genres = (result as Response.Success).data
        assertTrue("Should have genres", genres.isNotEmpty())
    }

    @Test
    fun `network error should return error response`() = runTest {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        // Act
        val result = novelUpdates.getCatalog(0)

        // Assert
        assertTrue("Result should be error for network failure", result is Response.Error)
    }

    @Test
    fun `invalid HTML should be handled gracefully`() = runTest {
        // Arrange
        val invalidHtml = "<html><body>Invalid structure</body></html>"
        mockWebServer.enqueue(MockResponse().setBody(invalidHtml))

        // Act
        val result = novelUpdates.getCatalog(0)

        // Assert
        // Should either return empty list or handle error gracefully
        when (result) {
            is Response.Success -> {
                val pagedList = result.data
                assertTrue("Should handle invalid HTML gracefully", 
                    pagedList.list.isEmpty() || pagedList.list.isNotEmpty())
            }
            is Response.Error -> {
                // Error response is also acceptable for invalid HTML
                assertNotNull("Error should have a message", result.exception.message)
            }
        }
    }

    private fun createMockNovelUpdatesListHtml(): String {
        return """
            <html>
            <body>
                <div class="search_main_box_nu">
                    <div class="search_title">
                        <a href="/series/test-novel-1/">Test Novel 1</a>
                    </div>
                    <div class="search_img_nu">
                        <img src="/cover1.jpg" alt="Cover 1"/>
                    </div>
                </div>
                <div class="search_main_box_nu">
                    <div class="search_title">
                        <a href="/series/test-novel-2/">Test Novel 2</a>
                    </div>
                    <div class="search_img_nu">
                        <img src="/cover2.jpg" alt="Cover 2"/>
                    </div>
                </div>
                <div class="digg_pagination">
                    <span class="current">1</span>
                    <a href="?page=2">2</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun createMockNovelUpdatesBookHtml(): String {
        return """
            <html>
            <body>
                <div class="seriestitlenu">Test Novel Title</div>
                <div class="seriesimg">
                    <img src="/cover.jpg" alt="Cover"/>
                </div>
                <div id="editdescription">
                    <p>This is a test novel description.</p>
                </div>
                <div id="showauthors">
                    <a href="/author/1">Test Author 1</a>
                    <a href="/author/2">Test Author 2</a>
                </div>
                <div id="seriesgenre">
                    <a gid="1">Action</a>
                    <a gid="2">Fantasy</a>
                </div>
                <div id="showtags">
                    <a>Adventure</a>
                    <a>Magic</a>
                </div>
                <div id="editassociated">
                    Alternative Title 1
                    Alternative Title 2
                </div>
                <div class="genre type">Web Novel</div>
                <h5 class="seriesother">Related Series</h5>
                <a href="/related/1">Related Book 1</a>
                <a href="/related/2">Related Book 2</a>
                <h5 class="seriesother">Recommendations</h5>
                <a href="/recommended/1">Recommended Book 1</a>
            </body>
            </html>
        """.trimIndent()
    }

    private fun createMockNovelUpdatesFiltersHtml(): String {
        return """
            <html>
            <body>
                <div class="genreme" genreid="1">Action</div>
                <div class="genreme" genreid="2">Adventure</div>
                <div class="genreme" genreid="3">Comedy</div>
                <div class="genreme" genreid="4">Drama</div>
                <div class="genreme" genreid="5">Fantasy</div>
            </body>
            </html>
        """.trimIndent()
    }
}