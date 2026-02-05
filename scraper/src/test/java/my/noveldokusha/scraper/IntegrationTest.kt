package my.noveldokusha.scraper

import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.Response
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

class IntegrationTest {

    @Test
    fun `complete scraper workflow should work end-to-end`() = runTest {
        // This test demonstrates a complete workflow using mock components
        
        // Arrange
        val mockDatabase = TestUtils.MockDatabase()
        val mockSource = TestUtils.MockSourceCatalog()
        
        // Act & Assert - Test database workflow
        
        // 1. Get catalog
        val catalogResult = mockDatabase.getCatalog(0)
        assertTrue("Catalog should be successful", catalogResult is Response.Success)
        val catalog = (catalogResult as Response.Success).data
        assertTrue("Should have books in catalog", catalog.list.isNotEmpty())
        
        // 2. Search functionality
        val searchResult = mockDatabase.searchByTitle(0, "Test")
        assertTrue("Search should be successful", searchResult is Response.Success)
        
        // 3. Get book details
        val bookResult = mockDatabase.getBookData("https://test.example.com/book/1")
        assertTrue("Book data should be successful", bookResult is Response.Success)
        val bookData = (bookResult as Response.Success).data
        assertNotNull("Book should have title", bookData.title)
        assertTrue("Book should have authors", bookData.authors.isNotEmpty())
        
        // Test source workflow
        
        // 4. Get source catalog
        val sourceCatalogResult = mockSource.getCatalogList(0)
        assertTrue("Source catalog should be successful", sourceCatalogResult is Response.Success)
        
        // 5. Get chapters for a book
        val chaptersResult = mockSource.getChapterList("https://test.example.com/book/1")
        assertTrue("Chapters should be successful", chaptersResult is Response.Success)
        val chapters = (chaptersResult as Response.Success).data
        assertTrue("Should have chapters", chapters.isNotEmpty())
        
        // 6. Test chapter content extraction
        val mockChapterDoc = TestUtils.createDocument(TestUtils.createMockChapterHtml())
        val chapterTitle = mockSource.getChapterTitle(mockChapterDoc)
        val chapterText = mockSource.getChapterText(mockChapterDoc)
        
        assertNotNull("Chapter should have title", chapterTitle)
        assertNotNull("Chapter should have text", chapterText)
    }

    @Test
    fun `scraper should handle various error conditions gracefully`() = runTest {
        // Test error handling in different scenarios
        
        val mockDatabase = TestUtils.MockDatabase()
        
        // Test with empty search
        val emptySearchResult = mockDatabase.searchByTitle(0, "")
        assertTrue("Empty search should return success with empty or filtered results", 
            emptySearchResult is Response.Success)
        
        // Test with invalid book URL (mock will still return success, but real implementation might fail)
        val invalidBookResult = mockDatabase.getBookData("invalid-url")
        // This should still work with mock, but demonstrates error handling pattern
        assertNotNull("Should handle invalid URL gracefully", invalidBookResult)
    }

    @Test
    fun `HTML parsing should handle malformed content`() {
        // Test resilience to malformed HTML
        
        val malformedHtml = "<div><p>Unclosed paragraph<div>Nested without closing</div>"
        val doc = TestUtils.createDocument(malformedHtml)
        
        // Should not throw exception
        val text = doc.text()
        assertTrue("Should extract text even from malformed HTML", text.isNotEmpty())
    }

    @Test
    fun `source compatibility checking should work correctly`() {
        // Test URL compatibility logic similar to what's in Scraper
        
        val testUrls = listOf(
            "https://www.royalroad.com/fiction/12345" to "https://www.royalroad.com/",
            "https://www.royalroad.com/fiction/12345/" to "https://www.royalroad.com",
            "https://example.com/book/1" to "https://example.com/",
            "https://different.com/book/1" to "https://example.com/"
        )
        
        testUrls.forEach { (url, baseUrl) ->
            val isCompatible = url.isCompatibleWithBaseUrl(baseUrl)
            val expected = when {
                url.contains("royalroad") && baseUrl.contains("royalroad") -> true
                url.contains("example.com") && baseUrl.contains("example.com") -> true
                else -> false
            }
            assertEquals("URL $url should ${if (expected) "" else "not "}be compatible with $baseUrl", 
                expected, isCompatible)
        }
    }
    
    private fun String.isCompatibleWithBaseUrl(baseUrl: String): Boolean {
        val normalizedUrl = if (this.endsWith("/")) this else "$this/"
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return normalizedUrl.startsWith(normalizedBaseUrl)
    }
}