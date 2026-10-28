package my.noveldokusha.tooling.ai_engine.engine

import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.Response
import my.noveldokusha.tooling.ai_engine.domain.AiChatMessage
import my.noveldokusha.tooling.ai_engine.domain.AiChatRequest
import my.noveldokusha.tooling.ai_engine.domain.AiChatResult
import my.noveldokusha.tooling.ai_engine.domain.AiChatRole
import my.noveldokusha.tooling.ai_engine.domain.AiModelInfo
import my.noveldokusha.tooling.ai_engine.domain.AiProvider
import my.noveldokusha.tooling.ai_engine.domain.AiProviderConfig
import my.noveldokusha.tooling.ai_engine.domain.AiRequest
import my.noveldokusha.tooling.ai_engine.domain.AiResult
import my.noveldokusha.tooling.ai_engine.domain.AiToolCall
import my.noveldokusha.tooling.ai_engine.domain.AiToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AiAgentLoopTest {

    private class ScriptedProvider(
        private val script: MutableList<(AiChatRequest) -> Response<AiChatResult>>,
    ) : AiProvider {
        val requests = mutableListOf<AiChatRequest>()
        override val config = AiProviderConfig("test", "Test", "http://localhost")
        override suspend fun listModels(): Response<List<AiModelInfo>> = Response.Success(emptyList())
        override suspend fun complete(request: AiRequest): Response<AiResult> =
            Response.Error("unused", IOException())

        override suspend fun chat(request: AiChatRequest): Response<AiChatResult> {
            requests += request
            return script.removeAt(0)(request)
        }
    }

    private fun reply(text: String, calls: List<AiToolCall> = emptyList()) =
        { _: AiChatRequest -> Response.Success(AiChatResult(AiChatMessage.assistant(text, calls), "m")) }

    private val echoTool = object : AiTool {
        override val spec = AiToolSpec("echo", "echoes", TextToolProtocol.schema { })
        override suspend fun execute(argumentsJson: String) = "echoed:$argumentsJson"
    }

    @Test
    fun `direct answer without tools`() = runBlocking {
        val provider = ScriptedProvider(mutableListOf(reply("hello")))
        val result = AiAgentLoop().run(provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool))
        val run = (result as Response.Success).data
        assertEquals("hello", run.finalAnswer)
        assertEquals(1, run.newMessages.size)
        assertEquals(1, provider.requests[0].tools.size)
    }

    @Test
    fun `native tool call round trip`() = runBlocking {
        val call = AiToolCall("c1", "echo", """{"a":1}""")
        val provider = ScriptedProvider(mutableListOf(reply("", listOf(call)), reply("done")))
        val events = mutableListOf<AiAgentEvent>()
        val result = AiAgentLoop().run(
            provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool)
        ) { events += it }
        val run = (result as Response.Success).data

        assertEquals("done", run.finalAnswer)
        assertEquals(listOf(AiChatRole.ASSISTANT, AiChatRole.TOOL, AiChatRole.ASSISTANT), run.newMessages.map { it.role })
        assertEquals("""echoed:{"a":1}""", run.newMessages[1].content)
        assertEquals("c1", run.newMessages[1].toolCallId)
        assertTrue(events.any { it is AiAgentEvent.ToolCallStarted })
        // Second request must include the tool result message.
        assertTrue(provider.requests[1].messages.any { it.role == AiChatRole.TOOL })
    }

    @Test
    fun `falls back to text protocol when tools rejected`() = runBlocking {
        val provider = ScriptedProvider(
            mutableListOf(
                { _ -> Response.Error("Request failed (HTTP 400): unknown parameter tools", IOException()) },
                reply("```tool_call\n{\"name\":\"echo\",\"arguments\":{\"b\":2}}\n```"),
                reply("final"),
            )
        )
        val events = mutableListOf<AiAgentEvent>()
        val result = AiAgentLoop().run(
            provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool)
        ) { events += it }
        val run = (result as Response.Success).data

        assertEquals("final", run.finalAnswer)
        assertTrue(events.any { it is AiAgentEvent.FallbackToTextProtocol })
        // Fallback requests carry no native tools and teach the protocol in the system prompt.
        assertTrue(provider.requests[1].tools.isEmpty())
        assertTrue(provider.requests[1].messages[0].content.contains("## Tools"))
        // Tool results are sent as user messages in the text protocol.
        assertTrue(provider.requests[2].messages.none { it.role == AiChatRole.TOOL })
        assertTrue(provider.requests[2].messages.any { it.content.contains("""echoed:{"b":2}""") })
    }

    @Test
    fun `unknown tool reports error to model`() = runBlocking {
        val call = AiToolCall("c1", "nope", "{}")
        val provider = ScriptedProvider(mutableListOf(reply("", listOf(call)), reply("ok")))
        val result = AiAgentLoop().run(provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool))
        val run = (result as Response.Success).data
        assertTrue(run.newMessages[1].content.startsWith("Error: unknown tool"))
    }

    @Test
    fun `forces a tool-less answer after max steps`() = runBlocking {
        val call = AiToolCall("c", "echo", "{}")
        val provider = ScriptedProvider((MutableList(3) { reply("", listOf(call)) } + reply("final")).toMutableList())
        val emitted = mutableListOf<AiChatMessage>()
        val result = AiAgentLoop(maxSteps = 3).run(
            provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool),
            onMessage = { emitted += it },
        )
        val run = (result as Response.Success).data
        assertEquals("final", run.finalAnswer)
        assertEquals(run.newMessages, emitted)
        // The forced request must not advertise tools.
        assertTrue(provider.requests.last().tools.isEmpty())
    }

    @Test
    fun `continuing at the step limit grants another budget`() = runBlocking {
        val call = AiToolCall("c", "echo", "{}")
        val provider = ScriptedProvider((MutableList(4) { reply("", listOf(call)) } + reply("done")).toMutableList())
        val limitHits = mutableListOf<Int>()
        val result = AiAgentLoop(maxSteps = 2).run(
            provider, "m", "sys", listOf(AiChatMessage.user("hi")), listOf(echoTool),
            onStepLimit = { used -> limitHits += used; if (limitHits.size == 1) StepLimitDecision.CONTINUE else StepLimitDecision.ANSWER_NOW },
        )
        assertEquals("done", (result as Response.Success).data.finalAnswer)
        assertEquals(listOf(2, 4), limitHits)
        assertTrue(provider.requests.last().tools.isEmpty())
    }

    @Test
    fun `auth errors are not treated as unsupported tools`() {
        assertTrue(!AiAgentLoop.looksLikeToolsUnsupported("Authentication failed: check the API key"))
        assertTrue(AiAgentLoop.looksLikeToolsUnsupported("tools is not supported by this model"))
    }
}
