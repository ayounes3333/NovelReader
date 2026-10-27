package my.noveldokusha.tooling.ai_engine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    @Test
    fun `render substitutes known variables`() {
        val template = "Translate into {{language}} the following: {{content}}"
        val result = PromptTemplate.render(
            template,
            mapOf(
                PromptTemplate.VAR_LANGUAGE to "Spanish",
                PromptTemplate.VAR_CONTENT to "Hello"
            )
        )
        assertEquals("Translate into Spanish the following: Hello", result)
    }

    @Test
    fun `render leaves unknown variables untouched`() {
        val template = "Keep {{unknown}} and {{content}}"
        val result = PromptTemplate.render(template, mapOf(PromptTemplate.VAR_CONTENT to "X"))
        assertEquals("Keep {{unknown}} and X", result)
    }

    @Test
    fun `render resolves missing variables to empty string`() {
        val result = PromptTemplate.render("a {{title}} b", emptyMap())
        assertEquals("a  b", result)
    }

    @Test
    fun `isValid requires content variable`() {
        assertTrue(PromptTemplate.isValid("Fix {{content}} please"))
        assertFalse(PromptTemplate.isValid("Fix the text please"))
    }
}
