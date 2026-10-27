package my.noveldokusha.tooling.ai_engine.engine

import my.noveldokusha.core.Response
import my.noveldokusha.core.flatMap
import my.noveldokusha.tooling.ai_engine.domain.AiModelInfo
import my.noveldokusha.tooling.ai_engine.domain.AiProvider
import my.noveldokusha.tooling.ai_engine.domain.AiProviderConfig
import my.noveldokusha.tooling.ai_engine.domain.AiRequest
import my.noveldokusha.tooling.ai_engine.domain.AiResult
import my.noveldokusha.tooling.ai_engine.domain.AiUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
