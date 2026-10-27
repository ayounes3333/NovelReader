package my.noveldokusha.tooling.ai_engine.domain

/**
 * Coarse classification of AI task failures, used to surface actionable
 * errors to the user (e.g. bad API key vs. transient network issue).
 */
enum class AiErrorCategory {
    AUTH,
    RATE_LIMIT,
    NETWORK,
    SERVER,
    UNKNOWN;

    companion object {
        private val AUTH_KEYWORDS = listOf(
            "401", "403", "unauthorized", "forbidden", "api key",
            "invalid_api_key", "authentication", "incorrect api"
        )
        private val RATE_LIMIT_KEYWORDS = listOf(
            "429", "rate limit", "ratelimit", "quota", "too many requests"
        )
        private val NETWORK_KEYWORDS = listOf(
            "timeout", "timed out", "connect", "network", "unreachable",
            "unable to resolve", "econn", "socket", "eof", "connection"
        )
        private val SERVER_KEYWORDS = listOf(
            "500", "502", "503", "504", "server error", "overloaded",
            "bad gateway", "service unavailable", "internal error"
        )

        /** Best-effort classification from the error message text. */
        fun fromMessage(message: String): AiErrorCategory {
            val text = message.lowercase()
            return when {
                AUTH_KEYWORDS.any { text.contains(it) } -> AUTH
                RATE_LIMIT_KEYWORDS.any { text.contains(it) } -> RATE_LIMIT
                SERVER_KEYWORDS.any { text.contains(it) } -> SERVER
                NETWORK_KEYWORDS.any { text.contains(it) } -> NETWORK
                else -> UNKNOWN
            }
        }
    }
}
