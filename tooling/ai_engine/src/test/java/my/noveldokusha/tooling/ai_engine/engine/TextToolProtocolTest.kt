package my.noveldokusha.tooling.ai_engine.engine

import kotlinx.serialization.json.put
import my.noveldokusha.tooling.ai_engine.domain.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRole
import my.noveldokusha.tooling.ai_engine.domain.AiToolCall
import my.noveldokusha.tooling.ai_engine.domain.AiToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolProtocolTest {

    @Test
    fun `parses fenced tool call with object arguments`() {
        val text = """
            Let me check.
            ```tool_call
            {"name": "read_chapter", "arguments": {"offset": -1}}
            ```
        """.trimIndent()
        val calls = TextToolProtocol.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("read_chapter", calls[0].name)
        assertEquals("""{"offset":-1}""", calls[0].argumentsJson)
    }

    @Test
    fun `parses bare json reply`() {
        val calls = TextToolProtocol.parseToolCalls("""{"name":"web_search","arguments":{"query":"x"}}""")
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].name)
    }

    @Test
    fun `plain answer yields no calls`() {
        assertTrue(TextToolProtocol.parseToolCalls("The hero is Kai.").isEmpty())
    }

    @Test
    fun `missing arguments default to empty object`() {
        val calls = TextToolProtocol.parseToolCalls("```tool_call\n{\"name\":\"get_novel_info\"}\n```")
        assertEquals("{}", calls[0].argumentsJson)
    }

    @Test
    fun `strip removes blocks and keeps prose`() {
        val text = "Checking...\n```tool_call\n{\"name\":\"a\"}\n```\nDone."
        assertEquals("Checking...\n\nDone.", TextToolProtocol.stripToolBlocks(text))
    }

    @Test
    fun `flatten converts tool roles to plain chat roles`() {
        val call = AiToolCall("1", "read_chapter", """{"offset":0}""")
        val history = listOf(
            AiChatMessage.user("hi"),
            AiChatMessage.assistant("", listOf(call)),
            AiChatMessage.tool(call, "chapter text"),
        )
        val flat = TextToolProtocol.flattenHistory(history)
        assertTrue(flat.none { it.role == AiChatRole.TOOL })
        assertTrue(flat.all { it.toolCalls.isEmpty() })
        assertTrue(flat[1].content.contains("```tool_call"))
        assertTrue(flat[2].content.contains("chapter text"))
    }

    @Test
    fun `instructions list every tool`() {
        val tools = listOf(
            AiToolSpec("a", "does a", TextToolProtocol.schema { put("x", 1) }),
            AiToolSpec("b", "does b", TextToolProtocol.schema { }),
        )
        val text = TextToolProtocol.instructions(tools)
        assertTrue(text.contains("- a: does a"))
        assertTrue(text.contains("- b: does b"))
    }
}
