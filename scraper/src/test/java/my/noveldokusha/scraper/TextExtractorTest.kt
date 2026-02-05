package my.noveldokusha.scraper

import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextExtractorTest {

    @Test
    fun `extractText should handle simple HTML correctly`() {
        // Arrange
        val html = "<p>This is a test paragraph.</p>"
        val doc = TestUtils.createDocument(html)
        val element = doc.selectFirst("p")!!

        // Act
        val result = TextExtractor.get(element)

        // Assert
        assertEquals("This is a test paragraph.", result.trim())
    }

    @Test
    fun `extractText should handle nested HTML elements`() {
        // Arrange
        val html = """
            <div>
                <p>First paragraph.</p>
                <p>Second <strong>bold</strong> paragraph.</p>
                <p>Third paragraph with <em>emphasis</em>.</p>
            </div>
        """.trimIndent()
        val doc = TestUtils.createDocument(html)
        val element = doc.selectFirst("div")!!

        // Act
        val result = TextExtractor.get(element)

        // Assert
        assertTrue("Should contain all text", result.contains("First paragraph"))
        assertTrue("Should contain bold text", result.contains("bold"))
        assertTrue("Should contain emphasized text", result.contains("emphasis"))
        assertFalse("Should not contain HTML tags", result.contains("<strong>"))
    }

    @Test
    fun `extractText should handle empty elements`() {
        // Arrange
        val html = "<div></div>"
        val doc = TestUtils.createDocument(html)
        val element = doc.selectFirst("div")!!

        // Act
        val result = TextExtractor.get(element)

        // Assert
        assertTrue("Empty element should return empty string", result.trim().isEmpty())
    }

    @Test
    fun `extractText should preserve line breaks`() {
        // Arrange
        val html = """
            <div>
                <p>First line.</p>
                <br>
                <p>Second line.</p>
            </div>
        """.trimIndent()
        val doc = TestUtils.createDocument(html)
        val element = doc.selectFirst("div")!!

        // Act
        val result = TextExtractor.get(element)

        // Assert
        assertTrue("Should contain first line", result.contains("First line"))
        assertTrue("Should contain second line", result.contains("Second line"))
    }

    @Test
    fun `extractText should handle special characters`() {
        // Arrange
        val html = "<p>Special chars: &amp; &lt; &gt; &quot; &#39;</p>"
        val doc = TestUtils.createDocument(html)
        val element = doc.selectFirst("p")!!

        // Act
        val result = TextExtractor.get(element)

        // Assert
        assertTrue("Should decode HTML entities", result.contains("& < > \" '"))
    }
}