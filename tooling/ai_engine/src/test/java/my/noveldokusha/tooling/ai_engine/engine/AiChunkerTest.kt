package my.noveldokusha.tooling.ai_engine.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChunkerTest {

    @Test
    fun `split returns single chunk for short text`() {
        val text = "short chapter"
        val chunks = AiChunker.split(text, maxChars = 100)
        assertEquals(listOf(text), chunks)
    }

    @Test
    fun `split preserves all content at paragraph boundaries`() {
        val paragraph = "lorem ipsum dolor sit amet\n\n"
        val text = paragraph.repeat(50)
        val chunks = AiChunker.split(text.trim(), maxChars = 200)

        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue("chunk too large", it.length <= 200) }
        assertEquals(
            text.trim(),
            chunks.joinToString("\n\n")
        )
    }

    @Test
    fun `split handles oversized single paragraph by lines`() {
        val line = "x".repeat(60) + "\n"
        val text = line.repeat(10)
        val chunks = AiChunker.split(text.trim(), maxChars = 150)

        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue("chunk too large", it.length <= 150) }
        // No characters lost; only separator placement differs between fragments
        assertEquals(
            text.trim().filterNot { it.isWhitespace() },
            chunks.joinToString("").filterNot { it.isWhitespace() }
        )
    }

    @Test
    fun `sampleHeadAndTail keeps short text intact`() {
        val text = "small text"
        assertEquals(text, AiChunker.sampleHeadAndTail(text, headChars = 100, tailChars = 50))
    }

    @Test
    fun `sampleHeadAndTail keeps head and tail of long text`() {
        val text = "H".repeat(300) + "M" + "T".repeat(300)
        val sampled = AiChunker.sampleHeadAndTail(text, headChars = 100, tailChars = 50)

        assertTrue(sampled.startsWith("H".repeat(10)))
        assertTrue(sampled.endsWith("T".repeat(50)))
        assertTrue(sampled.contains("[...]"))
        assertTrue(sampled.length < text.length)
    }
}
