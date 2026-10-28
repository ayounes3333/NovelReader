package my.noveldokusha.tooling.ai_engine.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated OkHttp client for AI providers. Deliberately separate from
 * `ScraperNetworkClient`: no scraper interceptors, cookies or cache, and
 * longer timeouts to accommodate LLM latency. Logging is disabled entirely
 * so API keys and user content never reach the logs.
 */
@Singleton
class AiHttpClient @Inject constructor() {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * Executes [request] and returns the response body as text. HTTP error
     * statuses are mapped to user-readable [Response.Error] messages.
     */
    suspend fun execute(request: Request): Response<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> Response.Success(body)
                    else -> Response.Error(
                        message = errorMessage(response.code, body, response.header("Retry-After")),
                        exception = IOException("HTTP ${response.code}")
                    )
                }
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            Response.Error(
                message = "Connection to AI provider failed: ${e.message ?: e.javaClass.simpleName}",
                exception = e as? IOException ?: IOException(e)
            )
        }
    }

    private fun errorMessage(code: Int, body: String, retryAfter: String?): String {
        val detail = providerMessage(body)
        return when (code) {
            401, 403 -> "Authentication failed: check the API key for this provider." + detail
            404 -> "Endpoint not found: verify the provider base URL." + detail
            // Some providers (e.g. Z.ai) also use 429 for quota/balance problems,
            // so the provider's own message is essential here.
            429 -> buildString {
                append("Provider refused the request (HTTP 429: rate limit or quota/balance).")
                if (!retryAfter.isNullOrEmpty()) append(" Retry in $retryAfter seconds.")
                append(detail)
            }

            in 500..599 -> "AI provider server error ($code). Try again later." + detail
            else -> "Request failed (HTTP $code): ${body.take(300)}"
        }
    }

    /** Extracts `error.message` (and code) from an OpenAI-style error body, if present. */
    private fun providerMessage(body: String): String {
        if (body.isBlank()) return ""
        val message = MESSAGE_REGEX.find(body)?.groupValues?.get(1)
        val errorCode = CODE_REGEX.find(body)?.groupValues?.get(1)
        val text = message ?: body.take(200)
        return "\nProvider says: " + (if (errorCode != null) "[$errorCode] " else "") + text
    }

    private companion object {
        val MESSAGE_REGEX = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val CODE_REGEX = Regex(""""code"\s*:\s*"?([A-Za-z0-9_\-]+)"?""")
    }
}
