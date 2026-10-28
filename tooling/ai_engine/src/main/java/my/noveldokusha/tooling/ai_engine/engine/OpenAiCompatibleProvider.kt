package my.noveldokusha.tooling.ai_engine.engine

import my.noveldokusha.core.Response
import my.noveldokusha.core.flatMap
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
import my.noveldokusha.tooling.ai_engine.domain.AiUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Single provider implementation covering OpenAI, DeepSeek, Z.ai, OpenCode and
 * any other OpenAI-compatible `/v1/chat/completions` endpoint. Differences are
 * expressed through [AiProviderConfig].
 *
 * Requests never go through the scraper client: no cookies, no interceptors and
 * logging disabled so neither API keys nor generated text end up in logs.
 *
 * Instantiated per configuration by [ProviderRegistry]; not a singleton.
 */
class OpenAiCompatibleProvider(
    private val client: AiHttpClient,
    private val secrets: AiSecretsStore,
    override val config: AiProviderConfig,
) : AiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun apiKey(): String? =
        secrets.get(config.id).takeIf { !it.isNullOrEmpty() }

    private fun authorizedRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (config.requiresApiKey) {
            val key = apiKey()
                ?: return builder // handled as error before executing the call
            builder.header("Authorization", "Bearer $key")
        }
        return builder
    }

    override suspend fun listModels(): Response<List<AiModelInfo>> {
        if (config.requiresApiKey && apiKey().isNullOrEmpty()) {
            return Response.Error(
                "API key not configured for ${config.name}",
                IllegalStateException("missing key")
            )
        }
        val request = authorizedRequest(config.baseUrl.trimEnd('/') + "/models")
            .get()
            .build()

        return client.execute(request).flatMap { body ->
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
                Response.Success(
                    data.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        AiModelInfo(
                            id = id,
                            ownedBy = obj["owned_by"]?.jsonPrimitive?.content
                        )
                    }
                )
            }.getOrElse {
                Response.Error(
                    "Could not parse model list: ${it.message}",
                    it as? Exception ?: IOException(it)
                )
            }
        }
    }

    override suspend fun complete(request: AiRequest): Response<AiResult> {
        if (config.requiresApiKey && apiKey().isNullOrEmpty()) {
            return Response.Error(
                "API key not configured for ${config.name}",
                IllegalStateException("missing key")
            )
        }

        val payload = buildJsonObject {
            put("model", request.model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", request.systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", request.userContent)
                })
            })
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
        }

        val httpRequest = authorizedRequest(config.baseUrl.trimEnd('/') + "/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return client.execute(httpRequest).flatMap { body ->
            parseCompletion(body, request.model)
        }
    }

    private fun parseCompletion(body: String, requestModel: String): Response<AiResult> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject

        root["error"]?.let { errorElement ->
            val message = errorElement.jsonObject["message"]?.jsonPrimitive?.content
                ?: "Provider error"
            return Response.Error(message, IOException(message))
        }

        val choices = root["choices"]?.jsonArray
        if (choices == null || choices.isEmpty()) {
            return Response.Error("Provider returned no choices", IOException("empty choices"))
        }

        val choice = choices.first().jsonObject
        val message = choice["message"]?.jsonObject
        val text = message?.get("content")?.jsonPrimitive?.content
        if (text == null) {
            return Response.Error("Provider returned an empty completion", IOException("empty content"))
        }

        val usage = root["usage"]?.jsonObject?.let { usageJson ->
            AiUsage(
                promptTokens = usageJson["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                completionTokens = usageJson["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                totalTokens = usageJson["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        }

        Response.Success(
            AiResult(
                text = text,
                model = root["model"]?.jsonPrimitive?.content ?: requestModel,
                finishReason = choice["finish_reason"]?.jsonPrimitive?.content,
                usage = usage,
            )
        )
    }.getOrElse {
        Response.Error(
            "Could not parse provider response: ${it.message}",
            it as? Exception ?: IOException(it)
        )
    }

    override suspend fun chat(request: AiChatRequest): Response<AiChatResult> {
        if (config.requiresApiKey && apiKey().isNullOrEmpty()) {
            return Response.Error(
                "API key not configured for ${config.name}",
                IllegalStateException("missing key")
            )
        }

        val payload = buildJsonObject {
            put("model", request.model)
            put("messages", buildJsonArray {
                request.messages.forEach { add(it.toJson()) }
            })
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
        }

        val httpRequest = authorizedRequest(config.baseUrl.trimEnd('/') + "/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return client.execute(httpRequest).flatMap { body ->
            parseChatCompletion(body, request.model)
        }
    }

    private fun AiChatMessage.toJson(): JsonObject = buildJsonObject {
        put("role", role.wire)
        when (role) {
            AiChatRole.TOOL -> {
                put("content", content)
                toolCallId?.let { put("tool_call_id", it) }
                toolName?.let { put("name", it) }
            }

            AiChatRole.ASSISTANT -> {
                // Some providers reject an empty string content alongside tool calls.
                if (content.isNotEmpty() || toolCalls.isEmpty()) put("content", content)
                if (toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.name)
                                    put("arguments", call.argumentsJson)
                                })
                            })
                        }
                    })
                }
            }

            else -> put("content", content)
        }
    }

    private fun parseChatCompletion(body: String, requestModel: String): Response<AiChatResult> =
        runCatching {
            val root = json.parseToJsonElement(body).jsonObject

            root["error"]?.let { errorElement ->
                val message = errorElement.jsonObject["message"]?.jsonPrimitive?.content
                    ?: "Provider error"
                return Response.Error(message, IOException(message))
            }

            val choices = root["choices"]?.jsonArray
            if (choices == null || choices.isEmpty()) {
                return Response.Error("Provider returned no choices", IOException("empty choices"))
            }

            val choice = choices.first().jsonObject
            val message = choice["message"]?.jsonObject
                ?: return Response.Error("Provider returned no message", IOException("empty message"))

            val text = message["content"]?.let { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            }.orEmpty()

            val toolCalls = message["tool_calls"]?.let { parseToolCalls(it) }.orEmpty()

            if (text.isEmpty() && toolCalls.isEmpty()) {
                return Response.Error("Provider returned an empty completion", IOException("empty content"))
            }

            val usage = root["usage"]?.jsonObject?.let { usageJson ->
                AiUsage(
                    promptTokens = usageJson["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    completionTokens = usageJson["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    totalTokens = usageJson["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }

            Response.Success(
                AiChatResult(
                    message = AiChatMessage.assistant(text, toolCalls),
                    model = root["model"]?.jsonPrimitive?.content ?: requestModel,
                    finishReason = choice["finish_reason"]?.jsonPrimitive?.content,
                    usage = usage,
                )
            )
        }.getOrElse {
            Response.Error(
                "Could not parse provider response: ${it.message}",
                it as? Exception ?: IOException(it)
            )
        }

    private fun parseToolCalls(element: JsonElement): List<AiToolCall> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val function = obj["function"]?.jsonObject ?: return@mapIndexedNotNull null
            val name = function["name"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
            val arguments = function["arguments"]?.let { args ->
                // Most providers send a JSON-encoded string; a few inline the object.
                (args as? JsonPrimitive)?.takeIf { it.isString }?.content ?: args.toString()
            }.orEmpty().ifBlank { "{}" }
            AiToolCall(
                id = obj["id"]?.jsonPrimitive?.content ?: "call_$index",
                name = name,
                argumentsJson = arguments,
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
